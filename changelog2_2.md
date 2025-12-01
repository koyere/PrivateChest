# 📜 PrivateChest - Version 2.2 Release Notes

**Release Date:** December 1st, 2025

---

## [2.2] - 2025-12-01

### Added
- **Configurable Hopper Protection System**
  - New `hopper-protection.allow-hopper-access` option in `config.yml`
  - Allows players to enable hopper access for automated sorting systems with locked containers
  - Includes comprehensive security warnings and documentation in configuration file
  - Default setting remains secure (false) - blocks all automated item movement

### Improvements
- Enhanced `HopperProtectionListener` with configurable behavior
- Added detailed JavaDoc documentation for hopper protection logic
- Improved security transparency with clear warnings about enabling hopper access

### Configuration Changes
- New section `hopper-protection` added to `config.yml`
  - `allow-hopper-access: false` (default - maximum security)
  - Detailed documentation on security implications and recommended use cases

### Use Cases
This update is designed for:
- Servers with automated sorting/storage systems
- Private or trusted servers where theft is not a concern
- Servers with additional claim/protection plugins

### Security Notes
- **Default behavior unchanged:** Hoppers remain blocked from protected containers
- When enabled, ANY hopper can access locked containers (including those placed by other players)
- Recommended only for trusted environments or servers with additional protection layers
- Admin bypass permissions remain unaffected

### Compatibility
- Minecraft 1.16.x – 1.21.5
- Tested on Paper, Purpur, Pufferfish, and Spigot

---

## Installation & Configuration

1. **Update:** Replace your old PrivateChest JAR with version 2.2
2. **Reload or Restart:** Run `/privatechest reload` or restart your server
3. **Configure (Optional):** Edit `config.yml` to enable hopper access if needed:
   ```yaml
   hopper-protection:
     allow-hopper-access: true  # Set to true to enable
   ```

---

## Known Issues
None reported for this version.

---

## Support & Feedback
- Report issues on GitHub: [PrivateChest Issues](https://github.com/anthropics/claude-code/issues)
- SpigotMC Resource: https://www.spigotmc.org/resources/privatechest.124359/

Thank you for using PrivateChest!
