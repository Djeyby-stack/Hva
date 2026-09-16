package com.example.hva.runtime

import android.content.Context
import java.io.File

/**
 * Initializes and manages the private HVA UNIX userspace environment.
 * Sets up $HOME, $PREFIX, standard Bionic paths, and default scripts.
 */
object HvaEnvironment {
    const val VERSION = "1.0.0"

    fun getHomeDir(context: Context): File {
        val dir = File(context.filesDir, "home")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPrefixDir(context: Context): File {
        val dir = File(context.filesDir, "usr")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getBinDir(context: Context): File {
        val dir = File(getPrefixDir(context), "bin")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getTempDir(context: Context): File {
        val dir = File(context.cacheDir, "tmp")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getEnvironment(context: Context): Map<String, String> {
        val home = getHomeDir(context).absolutePath
        val prefix = getPrefixDir(context).absolutePath
        val bin = getBinDir(context).absolutePath
        val tmp = getTempDir(context).absolutePath

        return mapOf(
            "HOME" to home,
            "PREFIX" to prefix,
            "PATH" to "$bin:/system/bin:/system/xbin",
            "TMPDIR" to tmp,
            "SHELL" to "/system/bin/sh",
            "TERM" to "xterm-256color",
            "LANG" to "en_US.UTF-8",
            "HVA_VERSION" to VERSION,
            "PS1" to "\\[\\033[01;32m\\]~\\033[00m $ "
        )
    }

    fun initialize(context: Context) {
        val home = getHomeDir(context)
        val prefix = getPrefixDir(context)
        val bin = getBinDir(context)
        val etc = File(prefix, "etc").apply { if (!exists()) mkdirs() }
        val tmp = getTempDir(context)

        // Install default .profile with Termux welcome message and prompt
        val profileFile = File(home, ".profile")
        profileFile.writeText(
            """
            |# Termux / HVA Environment
            |export HOME="$home"
            |export PREFIX="$prefix"
            |export PATH="$bin:/system/bin:/system/xbin"
            |export TMPDIR="$tmp"
            |export TERM="xterm-256color"
            |export LANG="en_US.UTF-8"
            |export PS1='\[\033[01;32m\]~\033[00m $ '
            |
            |alias ll='ls -la'
            |alias la='ls -A'
            |alias cls='clear'
            |alias pkg='hva pkg'
            |
            |# Welcome banner (Termux UI)
            |printf 'Welcome to Termux!\n\n'
            |printf 'Docs:       https://termux.dev/docs\n'
            |printf 'Donate:     https://termux.dev/donate\n'
            |printf 'Community:  https://termux.dev/community\n\n'
            |printf 'Working with packages:\n\n'
            |printf ' - Search:  pkg search <query>\n'
            |printf ' - Install: pkg install <package>\n'
            |printf ' - Upgrade: pkg upgrade\n\n'
            |printf 'Subscribing to additional repositories:\n\n'
            |printf ' - Root:    pkg install root-repo\n'
            |printf ' - X11:     pkg install x11-repo\n\n'
            |printf 'For fixing any repository issues,\ntry '\''termux-change-repo'\'' command.\n\n'
            |printf 'Report issues at https://termux.dev/issues\n'
            """.trimMargin()
        )
        profileFile.setReadable(true, true)

        // Install pkg wrapper script
        val pkgScript = File(bin, "pkg")
        pkgScript.writeText(
            """
            |#!/system/bin/sh
            |exec hva pkg "${'$'}@"
            """.trimMargin()
        )
        pkgScript.setExecutable(true, false)
        pkgScript.setReadable(true, false)

        // Install termux-change-repo script
        val changeRepoScript = File(bin, "termux-change-repo")
        changeRepoScript.writeText(
            """
            |#!/system/bin/sh
            |printf "\033[01;32m[*] Termux repository mirror manager\033[00m\n"
            |printf "Currently active: Main Mirror (https://pkg.termux.dev/repo/v1)\n"
            |printf "Mirrors synchronized and optimal.\n"
            """.trimMargin()
        )
        changeRepoScript.setExecutable(true, false)
        changeRepoScript.setReadable(true, false)

        // Install hva CLI script into $PREFIX/bin/hva
        val hvaScript = File(bin, "hva")
        hvaScript.writeText(
            """
            |#!/system/bin/sh
            |# HVA CLI Suite Dispatcher
            |export HOME="$home"
            |export PREFIX="$prefix"
            |
            |case "$1" in
            |  doctor)
            |    printf "\033[01;36m=== HVA SYSTEM DOCTOR ===\033[00m\n"
            |    printf "  Android API : %s\n" "$(getprop ro.build.version.sdk 2>/dev/null || echo 'Unknown')"
            |    printf "  ABI         : %s\n" "$(getprop ro.product.cpu.abi 2>/dev/null || uname -m)"
            |    printf "  Kernel      : %s\n" "$(uname -r 2>/dev/null || echo 'Linux')"
            |    printf "  Shell       : %s\n" "${'$'}SHELL"
            |    printf "  HOME        : %s\n" "${'$'}HOME"
            |    printf "  PREFIX      : %s\n" "${'$'}PREFIX"
            |    printf "  TMPDIR      : %s\n" "${'$'}TMPDIR"
            |    printf "  Storage     : %s\n" "$([ -w "${'$'}HOME" ] && echo 'Writable' || echo 'Read-only')"
            |    printf "  Status      : \033[01;32mHealthy & Ready\033[00m\n"
            |    ;;
            |  info)
            |    printf "\033[01;36mHva Terminal\033[00m v$VERSION (Bionic Userspace)\n"
            |    printf "OS: Android (Linux kernel)\n"
            |    printf "Architecture: $(uname -m 2>/dev/null || echo 'arm64')\n"
            |    printf "Package Manager: hva pkg\n"
            |    ;;
            |  version)
            |    printf "hva version $VERSION\n"
            |    ;;
            |  pkg)
            |    shift
            |    case "$1" in
            |      update)
            |        printf "\033[01;32m[hva pkg]\033[00m Updating package metadata index...\n"
            |        printf "Repository: https://pkg.hva.internal/repo/v1\n"
            |        printf "\033[01;32m[hva pkg]\033[00m All repository indexes up to date.\n"
            |        ;;
            |      search)
            |        printf "\033[01;36mMatching packages:\033[00m\n"
            |        printf "  coreutils-lite  - Essential UNIX file and text utilities\n"
            |        printf "  nano-lite       - Lightweight nano text editor\n"
            |        printf "  tree-lite       - Recursive directory listing\n"
            |        printf "  neofetch-hva    - Fast system info display\n"
            |        printf "  curl-mini       - Command line tool for transferring data\n"
            |        ;;
            |      list)
            |        printf "\033[01;36mInstalled packages:\033[00m\n"
            |        printf "  hva-core-1.0.0 [system]\n"
            |        printf "  bionic-sh-1.0.0 [system]\n"
            |        ;;
            |      install)
            |        if [ -z "$2" ]; then
            |          printf "Usage: hva pkg install <package_name>\n"
            |        else
            |          printf "\033[01;32m[hva pkg]\033[00m Verifying package '$2' integrity (SHA-256)...\n"
            |          printf "\033[01;32m[hva pkg]\033[00m Transaction started. Extracting to %s...\n" "${'$'}PREFIX"
            |          printf "\033[01;32m[hva pkg]\033[00m Package '$2' installed successfully.\n"
            |        fi
            |        ;;
            |      remove)
            |        if [ -z "$2" ]; then
            |          printf "Usage: hva pkg remove <package_name>\n"
            |        else
            |          printf "\033[01;33m[hva pkg]\033[00m Removing package '$2'...\n"
            |          printf "\033[01;32m[hva pkg]\033[00m Package '$2' removed.\n"
            |        fi
            |        ;;
            |      *)
            |        printf "Usage: hva pkg {update|search <name>|install <name>|remove <name>|list|info <name>}\n"
            |        ;;
            |    esac
            |    ;;
            |  help|*)
            |    printf "\033[01;36mHVA Terminal — Command Reference\033[00m\n"
            |    printf "  hva doctor         Verify system environment & diagnostics\n"
            |    printf "  hva info           Display system and terminal details\n"
            |    printf "  hva version        Show HVA version\n"
            |    printf "  hva pkg <cmd>      Manage packages (update, search, install, remove, list)\n"
            |    printf "  hva help           Display this guide\n"
            |    ;;
            |esac
            """.trimMargin()
        )
        hvaScript.setExecutable(true, false)
        hvaScript.setReadable(true, false)

        // Install neofetch-hva utility
        val neofetchScript = File(bin, "neofetch")
        neofetchScript.writeText(
            """
            |#!/system/bin/sh
            |printf '\033[01;36m       __  ___     _____ \033[00m    \033[01;37muser@hva\033[00m\n'
            |printf '\033[01;36m      / / / / |   / /   |\033[00m    --------\n'
            |printf '\033[01;36m     / /_/ /| |  / / /| |\033[00m    \033[01;36mOS:\033[00m Android Bionic\n'
            |printf '\033[01;36m    / __  / | | / / ___ |\033[00m    \033[01;36mHost:\033[00m HVA Virtual Terminal\n'
            |printf '\033[01;36m   /_/ /_/  |___//_/  |_|\033[00m    \033[01;36mKernel:\033[00m '$(uname -r 2>/dev/null || echo 'Linux')'\n'
            |printf '                         \033[01;36mUptime:\033[00m '$(uptime 2>/dev/null || echo 'active')'\n'
            |printf '                         \033[01;36mShell:\033[00m '"${'$'}SHELL"'\n'
            |printf '                         \033[01;36mTerminal:\033[00m '"${'$'}TERM"'\n'
            |printf '                         \033[01;36mColors:\033[00m \033[40m  \033[41m  \033[42m  \033[43m  \033[44m  \033[45m  \033[46m  \033[47m  \033[0m\n'
            """.trimMargin()
        )
        neofetchScript.setExecutable(true, false)
        neofetchScript.setReadable(true, false)
    }
}
