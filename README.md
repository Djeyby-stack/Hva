# Hva — Terminal Android Moderne & Autonome (v0.0.8)

[![Build & Release Android Debug APK](https://github.com/Djeyby-stack/Hva/actions/workflows/build-and-release-apk.yml/badge.svg)](https://github.com/Djeyby-stack/Hva/actions/workflows/build-and-release-apk.yml)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android&logoColor=white)](https://android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Version](https://img.shields.io/badge/Version-0.0.8-blue.svg)](https://github.com/Djeyby-stack/Hva/releases)

**Hva** est un émulateur de terminal Android autonome, rapide et moderne conçu avec **Jetpack Compose** et **Kotlin Coroutines**. Il fournit un environnement UNIX complet (Bionic / BusyBox) sans nécessiter de root, un moteur de gestionnaire de paquets distant GitHub (`pkg`), un éditeur de texte interactif intégrable (`hva-edit` / `edit`), un moteur de saisie avec historique et autocomplétion, une barre de touches rapides multi-pages, des notifications persistantes de session et le support complet du copier/coller avec sélection tactile haute précision.

---

## 🌟 Nouveautés & Stabilité de la Version 0.0.8

- 📦 **Écosystème & Gestionnaire de Paquets Distant GitHub (`pkg` / `hva pkg`)** :
  - `pkg update` / `pkg sync` : Synchronisation en direct avec le dépôt GitHub distant (`https://raw.githubusercontent.com/Djeyby-stack/hva-packages/main/packages.json`).
  - `pkg install <nom>` : Téléchargement HTTP sécurisé et installation instantanée de binaires d'exécutables réels et scripts UNIX dans `$PREFIX/bin`.
  - `pkg repo list` / `pkg repo add <url>` / `pkg repo reset` : Gestion dynamique de plusieurs sources de dépôts de paquets sur GitHub.
  - `pkg search <requête>` : Recherche combinée dans le catalogue local et le catalogue GitHub synchrone.
  - `pkg list` & `pkg remove <nom>` : Transactionnalité complète avec contrôle de sécurité anti-Path Traversal (anti-Zip Slip) et rollback d'échec.
- 📝 **Éditeur de Texte Intégré (`hva-edit` / `edit` / `nano` / `micro`)** :
  - Mode d'édition et de lecture directe de fichiers texte dans le terminal sans quitter Hva.
  - Numérotation des lignes, coloration d'état et raccourcis d'édition rapide (`echo`, `cat`).
- 🩺 **Diagnostic Système & Hva Doctor (`hva doctor` / Diagnostics)** :
  - Diagnostic en direct de l'environnement Bionic, des permissions de stockage, de la mémoire Heap JVM et du processeur.
  - Outils intégrés `fastfetch`, `neofetch`, `htop`, `top`, `ping`, `ifconfig`, `tree`, `cmatrix`, `cowsay`, `figlet`, `sl`, `curl`, `python`.
- ⌨️ **Barre d'Outils Multi-Pages & Saisie Tactile (Termux Extra Keys)** :
  - Barre d'outils 2 lignes multi-pages avec toggles `CTRL` / `ALT`, flèches de navigation, `ESC`, `TAB`, `HOME`, `END`, `PGUP`, `PGDN` et symboles shell.
  - Boutons dédiés **`COPY`**, **`PASTE`**, **`SEL`** avec retour haptique et confirmation Toast.
- 🔔 **Sessions Multiples & Notifications d'arrière-plan** :
  - Gestionnaire de sessions avec onglets interactifs et tiroir de navigation (Drawer).
  - Notification Android persistante (`HvaNotificationManager`) indiquant le nombre de sessions actives et le PID en cours avec bouton pour fermer/quitter proprement.
- 🧹 **Nettoyage & Architecture Solide v0.0.8** :
  - Suppression intégrale de tous les artéfacts obsolètes et namespaces temporaires.
  - Namespace Android nettoyé (`com.example.hva`), thème centralisé `HvaTheme`, et CI/CD GitHub Actions automatisé pour les releases v0.0.8.

---

## 📱 Téléchargement de l'APK (v0.0.8)

1. Rendez-vous dans la section [**Releases**](https://github.com/Djeyby-stack/Hva/releases) du dépôt GitHub.
2. Téléchargez **`Hva-debug.apk`** sous la version **v0.0.8**.
3. Installez l'APK sur votre appareil Android (en autorisant l'installation d'applications de sources inconnues si demandé).

---

## 🧪 Guide de Test & Vérification Complète (v0.0.8)

### 1. Test de la saisie au clavier et affichage direct
1. Ouvrez l'application Hva. Le prompt vert `~$ ` s'affiche.
2. Tapez n'importe quelle commande (ex: `echo "Bonjour Hva v0.0.8"`).
3. **Vérification** : Chaque caractère s'affiche immédiatement au fur et à mesure de la frappe.
4. Appuyez sur **`Entrée`** : La commande s'exécute et affiche le résultat instantanément.

### 2. Test du Copier / Coller & Sélection
- **Coller depuis le presse-papier** :
  1. Copiez un texte quelconque depuis une autre application.
  2. Dans Hva, touchez le bouton **`PASTE`** dans la barre de touches en bas de l'écran ou dans le menu.
  3. **Vérification** : Le texte copié est immédiatement inséré à l'emplacement du curseur.
- **Sélectionner et Copier** :
  1. Appuyez sur **`SEL`** (Page 2 de la barre d'outils) pour tout sélectionner.
  2. Touchez **`COPY`** : Un message Toast confirme *"Texte sélectionné copié"*.

### 3. Test des paquets et mises à jour (`pkg`)
```bash
# 1. Mise à jour des index de dépôts distants
pkg update

# 2. Mise à niveau des paquets
pkg upgrade

# 3. Recherche d'un paquet
pkg search python

# 4. Installation d'un paquet (ex: tree, nano, python)
pkg install tree
pkg install nano

# 5. Liste des paquets installés
pkg list

# 6. Affichage des détails d'un paquet
pkg show nano

# 7. Désinstallation d'un paquet
pkg remove nano
```

### 4. Test du Diagnostic Système & Outils v0.0.8
```bash
# Diagnostic complet de santé du système et du matériel
hva doctor

# Spécifications système & logo ASCII
neofetch

# Arborescence des fichiers du répertoire courant
tree

# Évaluation d'expressions Python
python -c "print('Hello from Python on Hva v0.0.8!')"

# Test de requête HTTP
curl https://httpbin.org/get

# Navigation et historique
pwd
ls -la
history
```

### 5. Test de la Notification en tâche de fond
1. Au lancement de l'application, acceptez la permission des notifications (Android 13+).
2. Abaissez le volet des notifications Android.
3. **Vérification** : La notification persistante **Hva Terminal** apparaît avec l'icône, le nombre de sessions actives et le PID en cours.
4. Touchez la notification : l'application Hva revient immédiatement au premier plan.

---

## 🔍 Analyse Comparative : Hva vs Termux

| Critère | Termux | Hva (v0.0.8) |
| :--- | :--- | :--- |
| **Interface Utilisateur** | Ancienne UI Android Views (XML) | **Moderne 100% Jetpack Compose M3** |
| **Barre de touches rapides** | 1 ligne statique | **Multi-pages tactile fluide avec Haptic Feedback** |
| **Architecture Noyau** | C/NDK PTY (`/dev/ptmx`) | **Bionic Userspace Shell Engine (Kotlin + `/system/bin/sh`)** |
| **Compatibilité ANSI/VT100** | Native complète via NDK | Émulation ANSI xterm-256color optimisée |
| **Gestionnaire de paquets** | `apt` / `dpkg` avec bootstrap Debian | **Gestionnaire `pkg` Bionic autonome sans root** |
| **Permission Stockage** | Requiert configuration manuelle | **HOME sandboxé et accessible immédiatement** |

---

## 🛠️ Compilation en Local (Gradle)

```bash
# Compiler l'APK de débogage
gradle assembleDebug

# L'APK compilé sera généré dans :
# app/build/outputs/apk/debug/app-debug.apk
```
