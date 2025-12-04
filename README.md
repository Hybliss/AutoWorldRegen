# 🌍 AutoWorldRegen

Plugin Paper 1.21.1 pour régénérer automatiquement les chunks non-claimés.

## ✨ Fonctionnalités

- ✅ Régénération automatique programmée
- ✅ Respecte les claims GriefPrevention
- ✅ Buffer de sécurité configurable
- ✅ Avertissements progressifs
- ✅ Téléportation automatique des joueurs

## 📥 Installation

1. Télécharge le dernier `.jar` dans [Releases](../../releases)
2. Place-le dans `/plugins/`
3. Redémarre le serveur
4. Configure `plugins/AutoWorldRegen/config.yml`

## ⚙️ Configuration

```yaml
interval-days: 7          # Tous les 7 jours
world: world              # Monde à régénérer
buffer-chunks: 2          # Sécurité autour des claims
warnings: [30, 10, 5, 1]  # Avertissements (minutes)
