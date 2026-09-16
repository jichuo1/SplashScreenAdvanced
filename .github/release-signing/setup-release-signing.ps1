#Requires -Version 7.0

<#
.SYNOPSIS
    生成 SplashScreenAdvanced 的固定发布签名身份，并写入 GitHub Environment Secrets。

.DESCRIPTION
    发布流水线要求签名身份长期固定：APK 的签名证书指纹会被 verify_release_apk.py 与
    ANDROID_SIGNING_CERT_SHA256 逐字节比对，指纹一变，已安装用户就无法覆盖升级。
    因此本脚本的默认行为是「已存在就复用，绝不覆盖」。

    脚本会：
      1. 交互式读取密码（SecureString，两次确认，不回显、不落盘、不进历史记录）
      2. 在仓库目录之外生成 PKCS12 密钥库（RSA 4096 / SHA256withRSA / 50 年有效期）
      3. 回读校验密码与别名可用，导出证书并计算 SHA-256
      4. 通过 gh CLI 把 5 个 Secret 写入指定的 GitHub Environment
         （值走 stdin，不出现在命令行参数里，避免被进程列表窥见）

.PARAMETER SkipGitHubSecrets
    只生成并校验密钥库，不调用 gh。适合 gh 未登录、或你想在网页上手工填 Secret 的场景。
    此时脚本会打印除密码之外的所有值。

.EXAMPLE
    pwsh .github/release-signing/setup-release-signing.ps1

.EXAMPLE
    pwsh .github/release-signing/setup-release-signing.ps1 -SkipGitHubSecrets

.NOTES
    密钥库与密码请立刻分别备份到离线介质和密码管理器。两者任一丢失，
    这个模块就再也发不出能覆盖升级的新版本了。
#>

[CmdletBinding()]
param(
    [string]$Repository = "jichuo1/SplashScreenAdvanced",
    [string]$KeyStorePath = (Join-Path $env:USERPROFILE "Documents\AndroidSigning\SplashScreenAdvanced\splash-screen-release.p12"),
    [string]$KeyAlias = "splash_screen_release",
    [string[]]$Environments = @("alpha-release", "stable-release"),
    [switch]$SkipGitHubSecrets
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function ConvertTo-PlainText {
    param([Parameter(Mandatory)][Security.SecureString]$SecureValue)

    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Read-ConfirmedPassword {
    while ($true) {
        $first = ConvertTo-PlainText (Read-Host "请输入发布密钥密码（至少 16 位 ASCII 字符）" -AsSecureString)
        $second = ConvertTo-PlainText (Read-Host "请再次输入相同密码" -AsSecureString)
        if ($first -ne $second) {
            Write-Warning "两次密码不一致，请重新输入。"
            continue
        }
        if ($first.Length -lt 16 -or $first.Length -gt 128 -or $first -notmatch '^[\x20-\x7E]+$') {
            Write-Warning "密码必须为 16～128 位可打印 ASCII 字符。"
            continue
        }
        return $first
    }
}

function Set-GitHubEnvironmentSecret {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Value,
        [Parameter(Mandatory)][string]$EnvironmentName
    )

    # 值通过 stdin 传给 gh，不作为命令行参数：参数会出现在进程列表里
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $script:GitHubCliPath
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in @("secret", "set", $Name, "--env", $EnvironmentName, "--repo", $Repository)) {
        $null = $startInfo.ArgumentList.Add($argument)
    }

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $null = $process.Start()
    $process.StandardInput.Write($Value)
    $process.StandardInput.Close()
    $standardOutput = $process.StandardOutput.ReadToEnd()
    $standardError = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw (
            "写入 GitHub Environment '$EnvironmentName' 的 Secret '$Name' 失败。" +
                [Environment]::NewLine + $standardError
        )
    }
    if (-not [string]::IsNullOrWhiteSpace($standardOutput)) {
        Write-Verbose $standardOutput.Trim()
    }
}

# 密钥库绝不能落在仓库里 —— 一次手滑 git add 就等于把发布身份公开了
$repositoryRoot = [IO.Path]::GetFullPath(
    (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
).TrimEnd([IO.Path]::DirectorySeparatorChar)
$resolvedKeyStorePath = [IO.Path]::GetFullPath($KeyStorePath)
$repositoryPrefix = "$repositoryRoot$([IO.Path]::DirectorySeparatorChar)"
if ($resolvedKeyStorePath.StartsWith($repositoryPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "密钥库必须位于仓库目录之外：$resolvedKeyStorePath"
}

$null = Get-Command keytool -ErrorAction Stop
if (-not $SkipGitHubSecrets) {
    $script:GitHubCliPath = (Get-Command gh -ErrorAction Stop).Source
    & gh auth status --hostname github.com
    if ($LASTEXITCODE -ne 0) {
        throw "GitHub CLI 尚未登录 github.com。可改用 -SkipGitHubSecrets 先只生成密钥库。"
    }
}

$password = Read-ConfirmedPassword
$passwordEnvironmentName = "SPLASH_SCREEN_SETUP_KEY_PASSWORD"
$certificateFile = Join-Path ([IO.Path]::GetTempPath()) ("splash-screen-" + [Guid]::NewGuid().ToString("N") + ".cer")
try {
    # keytool 的 -storepass:env 从环境变量取密码，避免密码出现在命令行参数中
    Set-Item -Path "Env:$passwordEnvironmentName" -Value $password
    $keyStoreDirectory = Split-Path $resolvedKeyStorePath -Parent
    $null = New-Item -ItemType Directory -Path $keyStoreDirectory -Force

    if (-not (Test-Path -LiteralPath $resolvedKeyStorePath -PathType Leaf)) {
        & keytool `
            -genkeypair `
            -alias $KeyAlias `
            -keyalg RSA `
            -keysize 4096 `
            -sigalg SHA256withRSA `
            -validity 18263 `
            -dname "CN=SplashScreenAdvanced, OU=Release, O=SplashScreenAdvanced, C=CN" `
            -storetype PKCS12 `
            -keystore $resolvedKeyStorePath `
            -storepass:env $passwordEnvironmentName `
            -keypass:env $passwordEnvironmentName `
            -noprompt
        if ($LASTEXITCODE -ne 0) {
            throw "生成发布密钥库失败。"
        }
        Write-Host "已在仓库外生成新的发布密钥库：$resolvedKeyStorePath"
    }
    else {
        Write-Host "将复用现有发布密钥库，不会覆盖：$resolvedKeyStorePath"
    }

    # 回读一次：确认这套密码和别名真的能打开密钥库，别等到 CI 里才发现填错
    & keytool `
        -list `
        -alias $KeyAlias `
        -storetype PKCS12 `
        -keystore $resolvedKeyStorePath `
        -storepass:env $passwordEnvironmentName | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "无法使用指定密码和别名读取发布密钥库。"
    }

    & keytool `
        -exportcert `
        -alias $KeyAlias `
        -storetype PKCS12 `
        -keystore $resolvedKeyStorePath `
        -storepass:env $passwordEnvironmentName `
        -file $certificateFile | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "导出发布证书失败。"
    }

    # DER 证书的 SHA-256，与 apksigner --print-certs 报告的 "certificate SHA-256 digest" 同源，
    # 也正是 verify_release_apk.py 用来卡 ANDROID_SIGNING_CERT_SHA256 的那个值
    $certificateSha256 = (Get-FileHash -LiteralPath $certificateFile -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($certificateSha256 -notmatch '^[0-9a-f]{64}$') {
        throw "计算出的证书指纹不是 64 位十六进制：$certificateSha256"
    }
    $keyStoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($resolvedKeyStorePath))

    if ($SkipGitHubSecrets) {
        Write-Host ""
        Write-Host "已跳过写入 GitHub Secrets。请手工在下列 Environment 中创建这 5 个 Secret："
        Write-Host "  Environments: $($Environments -join ', ')"
        Write-Host ""
        Write-Host "  ANDROID_SIGNING_KEY_ALIAS      = $KeyAlias"
        Write-Host "  ANDROID_SIGNING_CERT_SHA256    = $certificateSha256"
        Write-Host "  ANDROID_SIGNING_STORE_PASSWORD = <你刚才输入的密码>"
        Write-Host "  ANDROID_SIGNING_KEY_PASSWORD   = <同上>"
        Write-Host "  ANDROID_SIGNING_KEY_BASE64     = <见下方文件>"
        Write-Host ""
        # base64 有数千字符，打到终端会被回滚缓冲区长期留存。
        # 落盘位置选密钥库所在目录而不是系统临时目录：那里本来就是你存放签名材料的地方，
        # 已在仓库之外，也不会被"清理临时文件"之类的工具随手翻出来。
        $base64File = Join-Path $keyStoreDirectory "keystore-base64.txt"
        [IO.File]::WriteAllText($base64File, $keyStoreBase64)
        Write-Host "  密钥库 base64 已写入：$base64File"
        Write-Host "  这个文件等同于密钥库本身，复制进 Secret 后请立即删除。"
    }
    else {
        foreach ($environmentName in $Environments) {
            Set-GitHubEnvironmentSecret -Name "ANDROID_SIGNING_KEY_BASE64" -Value $keyStoreBase64 -EnvironmentName $environmentName
            Set-GitHubEnvironmentSecret -Name "ANDROID_SIGNING_STORE_PASSWORD" -Value $password -EnvironmentName $environmentName
            Set-GitHubEnvironmentSecret -Name "ANDROID_SIGNING_KEY_ALIAS" -Value $KeyAlias -EnvironmentName $environmentName
            Set-GitHubEnvironmentSecret -Name "ANDROID_SIGNING_KEY_PASSWORD" -Value $password -EnvironmentName $environmentName
            Set-GitHubEnvironmentSecret -Name "ANDROID_SIGNING_CERT_SHA256" -Value $certificateSha256 -EnvironmentName $environmentName
            Write-Host "已更新 GitHub Environment：$environmentName"
        }
    }

    Write-Host ""
    Write-Host "固定发布证书 SHA-256：$certificateSha256"
    Write-Host "请立即把密钥库与密码分别保存到可靠的离线备份和密码管理器。"
    Write-Host "这两样任一丢失，本模块将无法再发布可覆盖升级的新版本。"
}
finally {
    Remove-Item -Path "Env:$passwordEnvironmentName" -ErrorAction SilentlyContinue
    # 不要静默吞掉清理失败：临时证书残留虽然不算泄密（证书本身是公开的），
    # 但"以为清理了其实没有"这种错觉比残留本身更危险
    if (Test-Path -LiteralPath $certificateFile) {
        Remove-Item -LiteralPath $certificateFile -Force -ErrorAction SilentlyContinue
        if (Test-Path -LiteralPath $certificateFile) {
            Write-Warning "未能删除临时证书文件，请手工清理：$certificateFile"
        }
    }
    $password = $null
}
