# Security Policy

## Supported Versions

| Version | Supported |
| ------- | --------- |
| v1.0.0  | Yes       |

## Reporting A Vulnerability

Please open a private security advisory on GitHub if the repository is hosted there. If private advisories are not available, open a minimal public issue that says you want to report a security issue without posting exploit details.

Useful details:

- Affected version or commit.
- Windows version.
- Android device model and Android version.
- Steps to reproduce.
- Whether USB debugging, privacy space, or multi-user mode is involved.

## Security Notes

USB Screen Stream captures the Android screen after user approval. This can expose private information visible on the phone.

ADB USB debugging also gives the connected computer elevated access to the Android device. Users should only authorize trusted computers.

This project does not provide stealth capture, remote control, network relay, authentication, or access control. Do not use it for monitoring devices without consent.
