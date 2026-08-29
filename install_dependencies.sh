#!/usr/bin/env bash
# ==============================================================================
# AIDITOR Automated Dependency Installer
# Detects host OS and installs Python 3, FFmpeg, librsvg, and Python packages.
# ==============================================================================

set -e

echo "⚡ [AIDITOR] Checking system environment and installing dependencies..."

# 1. Detect Environment / Package Manager
if [ -d "/data/data/com.termux" ]; then
    echo "📱 Detected Termux (Android) environment."
    pkg update -y
    pkg install -y python ffmpeg librsvg git
elif [ -f /etc/debian_version ] || [ -f /etc/lsb-release ]; then
    echo "🐧 Detected Debian / Ubuntu Linux environment."
    sudo apt update
    sudo apt install -y python3 python3-pip python3-venv ffmpeg librsvg2-bin git
elif [ -f /etc/arch-release ]; then
    echo "🐧 Detected Arch Linux environment."
    sudo pacman -Sy --noconfirm python python-pip ffmpeg librsvg git
elif [ -f /etc/fedora-release ]; then
    echo "🐧 Detected Fedora Linux environment."
    sudo dnf install -y python3 python3-pip ffmpeg librsvg2-tools git
elif [[ "$OSTYPE" == "darwin"* ]]; then
    echo "🍎 Detected macOS environment."
    if ! command -v brew &> /dev/null; then
        echo "❌ Homebrew is required. Please install Homebrew from https://brew.sh/"
        exit 1
    fi
    brew install python ffmpeg librsvg git
else
    echo "⚠️ Unknown OS. Please ensure python3, pip, ffmpeg, and librsvg are installed."
fi

# 2. Verify FFmpeg installation
if command -v ffmpeg &> /dev/null; then
    echo "✅ FFmpeg found: $(ffmpeg -version | head -n 1)"
else
    echo "❌ Error: FFmpeg was not found in PATH."
    exit 1
fi

# 3. Install Python Dependencies
echo "📦 Installing Python dependencies from requirements.txt..."
python3 -m pip install -r "$(dirname "$0")/requirements.txt"

# 4. Install AIDITOR in editable development mode
echo "🚀 Registering 'aiditor' CLI command..."
python3 -m pip install -e "$(dirname "$0")"

echo ""
echo "========================================================================"
echo "🎉 [AIDITOR] All dependencies and CLI tools installed successfully!"
echo "   Test your installation by running: aiditor --help"
echo "========================================================================"
