# Hva — Terminal Android Moderne & Autonome (v0.0.4)

[![Build & Release Android Debug APK](https://github.com/Djeyby-stack/Hva/actions/workflows/build-and-release-apk.yml/badge.svg)](https://github.com/Djeyby-stack/Hva/actions/workflows/build-and-release-apk.yml)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android&logoColor=white)](https://android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Version](https://img.shields.io/badge/Version-0.0.4-blue.svg)](https://github.com/Djeyby-stack/Hva/releases)

**Hva** est un émulateur de terminal Android autonome, rapide et moderne conçu avec **Jetpack Compose** et **Kotlin Coroutines**. Il fournit un environnement UNIX complet (Bionic / BusyBox) sans nécessiter de root, un moteur de saisie interactive avec historique et autocomplétion, une barre de touches rapides multi-pages, des notifications persistantes de session et le support complet du copier/coller.

---

## 🌟 Nouveautés de la Version 0.0.4

- ⚡ **Moteur de saisie interactif (`HvaShellEngine`)** :
  - Écho instantané des caractères tapés à l'écran en temps réel sans latence.
  - Gestion native des touches Retour arrière (`Backspace`/`DEL`), suppression avant (`Suppr`), et flèches de curseur (`←` / `→`).
  - Historique des commandes avec navigation fluide (`↑` / `↓`).
  - Primitives intégrées ultra-rapides (`cd`, `pwd`, `clear`, `export`, `history`, `help`, `exit`).
  - Exécution directe et asynchrone des commandes système POSIX (`ls`, `uname`, `ps`, `cat`, `echo`, etc.).
- 📦 **Gestionnaire de Paquets Intégré (`pkg` / `hva pkg`)** :
  - `pkg update` : Synchronisation des dépôts de paquets.
  - `pkg upgrade` : Mise à niveau de tous les paquets installés.
  - `pkg search <requête>` : Recherche dans le catalogue de paquets disponibles.
  - `pkg install <nom>` : Installation de paquets et binaires dans `$PREFIX/bin`.
  - `pkg list` : Liste de tous les paquets installés.
  - `pkg remove <nom>` : Désinstallation propre de paquets.
  - `pkg show <nom>` : Détails complets d'un paquet.
- 🩺 **Outils Système & Diagnostic Complet** :
  - `fastfetch` : Spécifications complètes ultra-rapides (CPU, GPU, RAM, Stockage, Batterie, Uptime, Palette ANSI).
  - `neofetch` : Affichage instantané des spécifications système avec logo ASCII coloré.
  - `hva doctor` : Diagnostic complet de l'OS Android, mémoire RAM, stockage, noyau Linux et permissions.
  - `htop` / `top` : Moniteur de processus et utilisation CPU/RAM.
  - `cmatrix` : Animation Matrix cybernétique verte.
  - `cowsay <texte>` & `fortune` : Générateur de citations et d'art ASCII UNIX.
  - `figlet <texte>` & `sl` : Bannières texte ASCII art et locomotive animée.
  - `termux-change-repo` : Gestionnaire de miroirs et dépôts de secours.
  - `tree` : Affichage récursif coloré de l'arborescence des dossiers et fichiers.
  - `curl` & `wget` : Téléchargement et inspection de flux HTTP/HTTPS directs.
  - `python` / `python3` : Interpréteur et calculateur d'expressions (`python -c "2+2"`).
- 🔔 **Système de Notification en arrière-plan (`HvaNotificationManager`)** :
  - Notification persistante dans la barre d'état Android indiquant le nombre de sessions actives, le titre de la console et le PID en cours.
  - Clic direct sur la notification pour réouvrir ou basculer sur l'application.
  - Demande automatique de la permission `POST_NOTIFICATIONS` sur Android 13+ (API 33+).
- 📋 **Support Copier / Coller & Sélection** :
  - Touches dédiées **`COPY`**, **`PASTE`**, **`SEL`** dans la barre de touches rapides.
  - Sélection tactile intuitive par glissement sur l'écran du terminal.
  - Actions rapides "Copier tout le terminal" et "Coller" dans le tiroir latéral.
- ⌨️ **Barre de touches rapides multi-pages (2 Pages)** :
  - **Page 1 (Contrôle & Navigation)** : `ESC`, `☰`, `↕`, `HOME`, `↑`, `END`, `⇄` (TAB), `CTRL`, `ALT`, `←`, `↓`, `→`, `PASTE`.
  - **Page 2 (Symboles & Presse-papier)** : `/`, `-`, `~`, `|`, `$`, `&`, `COPY`, `PASTE`, `SEL`, `PGUP`, `PGDN`, `;`, `:`.

---

## 📱 Téléchargement de l'APK (v0.0.4)

1. Rendez-vous dans la section [**Releases**](https://github.com/Djeyby-stack/Hva/releases) du dépôt GitHub.
2. Téléchargez **`Hva-debug.apk`** sous la version **v0.0.4**.
3. Installez l'APK sur votre appareil Android (en autorisant l'installation d'applications de sources inconnues si demandé).

---

## 🧪 Guide de Test & Vérification Complète (v0.0.4)

### 1. Test de la saisie au clavier et affichage direct
1. Ouvrez l'application Hva. Le prompt vert `~$ ` s'affiche.
2. Tapez n'importe quelle commande (ex: `echo "Bonjour Hva"`).
3. **Vérification** : Chaque caractère s'affiche immédiatement au fur et à mesure de la frappe.
4. Appuyez sur **`Entrée`** : La commande s'exécute et affiche le résultat instantanément.

### 2. Test du Copier / Coller & Sélection
- **Coller depuis le presse-papier** :
  1. Copiez un texte quelconque depuis une autre application (ex: votre navigateur).
  2. Dans Hva, touchez le bouton **`PASTE`** dans la barre de touches en bas de l'écran.
  3. **Vérification** : Le texte copié est immédiatement inséré à l'emplacement du curseur.
- **Sélectionner et Copier** :
  1. Faites glisser votre doigt sur une zone de texte à l'écran pour la mettre en surbrillance, ou appuyez sur **`SEL`** (Page 2 de la barre d'outils) pour tout sélectionner.
  2. Touchez **`COPY`** : Un message Toast confirme *"Texte copié dans le presse-papier"*.
  3. Collez ce texte où vous le souhaitez.

### 3. Test des paquets et mises à jour (`pkg`)
```bash
# 1. Mise à jour des index de dépôts
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

### 4. Test du Diagnostic Système & Outils
```bash
# Diagnostic complet de santé du système et du matériel
hva doctor

# Spécifications système & logo ASCII
neofetch

# Arborescence des fichiers du répertoire courant
tree

# Évaluation d'expressions Python
python -c "print('Hello from Python on Android!')"

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

## 🔍 Analyse Comparative Honnête : Hva vs Termux

| Critère | Termux | Hva (v0.0.4) |
| :--- | :--- | :--- |
| **Interface Utilisateur** | Ancienne UI Android Views (XML) | **Moderne 100% Jetpack Compose M3** |
| **Barre de touches rapides** | 1 ligne statique | **Multi-pages tactile fluide (2 Pages)** |
| **Architecture Noyau** | C/NDK PTY (`/dev/ptmx`, `libtermux-exec`) | **Bionic Userspace Shell Engine (Kotlin + `/system/bin/sh`)** |
| **Compatibilité ncurses (vim/htop)** | Native complète via NDK PTY | Émulation ANSI xterm-256color |
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
