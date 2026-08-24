# Exact Android-only upload method

## Recommended: Termux

This keeps the entire repository structure, including the hidden `.github/workflows/android.yml` file.

After extracting the ZIP to `Download/HZ-CHORD-AI-GITHUB-READY-UPDATED/Ichi-main`:

```sh
pkg update -y
pkg install git -y
cd ~/storage/shared/Download/HZ-CHORD-AI-GITHUB-READY-UPDATED/Ichi-main
git init
git branch -M main
git add .
git commit -m "Initial HZ Chord AI product build"
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
git push -u origin main
```

For authentication, use a GitHub personal access token when Git asks for a password, or use an SSH key already configured for your GitHub account.

After the push, GitHub Actions automatically runs `.github/workflows/android.yml`.
