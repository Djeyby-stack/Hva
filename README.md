# Hva — Terminal Android Moderne, 100% Autonome & Strict (v0.1.0)

[![Build & Release Android Debug APK](https://github.com/Djeyby-stack/Hva/actions/workflows/build-and-release-apk.yml/badge.svg)](https://github.com/Djeyby-stack/Hva/actions/workflows/build-and-release-apk.yml)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android&logoColor=white)](https://android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-36-success.svg)](https://developer.android.com)
[![Version](https://img.shields.io/badge/Version-0.1.0-blue.svg)](https://github.com/Djeyby-stack/Hva/releases)

**Hva** est un environnement et émulateur de terminal Android **100% autonome, modulaire et hautement maintenable**, bâti avec **Jetpack Compose (Material Design 3)** et **Kotlin Coroutines**.

Conçu pour fonctionner sans root et en conformité stricte avec les exigences de sécurité Android (Android 14/15/16, Target SDK 36), Hva intègre des moteurs d'exécution complets pour **JavaScript/Node.js**, **Python 3**, **Git VCS**, le stockage Android partagé (`~/storage`), les outils UNIX natifs (`grep`, `wc`, `jq`, `head`, `tail`, `curl`), un éditeur de texte interactif TUI (`micro`, `nano`, `vim`), et un pipeline de redirections/pipes shell.

---

## 🌟 Architecture & Moteurs Autonomes

### 1. 📂 Pont de Stockage Réel Android (`~/storage`)
* Initialisation instantanée avec `termux-setup-storage` ou `hva-setup-storage` créant des liens symboliques POSIX vers le stockage partagé :
  - `~/storage/shared` $\rightarrow$ `/storage/emulated/0`
  - `~/storage/downloads` $\rightarrow$ `/storage/emulated/0/Download`
  - `~/storage/dcim` $\rightarrow$ `/storage/emulated/0/DCIM`
  - `~/storage/pictures` $\rightarrow$ `/storage/emulated/0/Pictures`
  - `~/storage/music` $\rightarrow$ `/storage/emulated/0/Music`
  - `~/storage/movies` $\rightarrow$ `/storage/emulated/0/Movies`
  - `~/storage/documents` $\rightarrow$ `/storage/emulated/0/Documents`
  - `~/storage/external-1` $\rightarrow$ Carte SD secondaire (détection automatique)

### 2. ⚡ Moteurs d'Exécution Intégrés (Zéro Dépendance Externe)
* **Node.js & NPM (`node`, `npm`, `npx`)** :
  - REPL interactif et exécution de scripts `.js`.
  - Commandes `npm init`, `npm install <pkg>`, `npm run start`, `npm ls`.
* **Python 3 & PIP (`python`, `python3`, `pip`)** :
  - REPL interactif et exécution de code avec `python script.py` ou `python -c "..."`.
  - Gestionnaire `pip install`, `pip list`, `pip show`.
* **Moteur Git VCS Réel (`git`)** :
  - Gestion de versions réelle sur le système de fichiers : `git init`, `git status`, `git add`, `git commit`, `git log`, `git branch`, `git checkout`, `git diff`, `git clone`.
* **Éditeur de texte TUI plein écran (`micro`, `nano`, `vim`, `vi`, `edit`)** :
  - Navigation au curseur, édition, insertion, sauvegarde (`Ctrl+S`), quitter (`Ctrl+Q`).
* **Outils UNIX Natifs & Pipeline (`grep`, `wc`, `head`, `tail`, `jq`, `curl`, `tree`)** :
  - Support des expressions régulières, manipulation JSON, requêtes HTTP et affichage d'arborescence.
  - Gestionnaire de pipes (`|`) et de redirections (`>`, `>>`, `<`).

---

## 🔍 Comparaison Technique Impitoyable : Hva vs Termux

| Fonctionnalité | Termux | Hva (Architecture Moderne) |
| :--- | :--- | :--- |
| **Interface Utilisateur** | Anciennes Android Views (XML legacy) | **100% Jetpack Compose M3 avec Dynamic Colors & Rendu Canvas fluide** |
| **Sécurité & W^X** | Bloqué à `targetSdkVersion 28` (contournement non maintenable) | **Conforme `targetSdkVersion 36` (Android 15/16)** |
| **Autonomie de l'APK** | Dépend d'un bootstrap binaire lourd à télécharger | **100% Autonome immédiatement après installation** |
| **Édition de texte** | Dépend d'un paquet binaire externe `nano`/`vim` | **Éditeur TUI intégré dès le premier lancement** |
| **Gestionnaire de Paquets** | APT / DPKG compilé sur serveurs distants | **Moteur extensible `pkg` local + synchronisation distante** |
| **Intégration Stockage** | `termux-setup-storage` via symlinks | **Même structure standard (`~/storage/*`) avec fallback sécurisé** |

---

## 🧪 Suite de Tests Impitoyable & Détection des Régressions

Hva inclut une suite de tests unitaires et d'intégration automatisés exécutés sur chaque Pull Request et Commit dans GitHub Actions :

1. **`HvaEnginesStrictTest.kt`** :
   - Validation du parsing des arguments shell avec quotes imbriquées et échappements.
   - Validation des redirections (`>`, `>>`, `<`).
   - Validation des outils UNIX (`grep -i`, `grep -n`, `wc -l`, `head`, `tail`, `jq`).
   - Validation du workflow complet Git (`init` $\rightarrow$ `add` $\rightarrow$ `commit` $\rightarrow$ `status` $\rightarrow$ `branch` $\rightarrow$ `checkout`).
2. **`HvaTerminalTest.kt`** :
   - Émulation écran VT100/ANSI, déplacements curseur, couleurs SGR 16/256, titres OSC, décodage UTF-8 et gestion du buffer de scrollback.
3. **`ExampleRobolectricTest.kt`** :
   - Validation des ressources et du contexte d'application.

---

## 📱 Téléchargement de l'APK

1. Rendez-vous dans la section [**Releases**](https://github.com/Djeyby-stack/Hva/releases) du dépôt GitHub.
2. Téléchargez **`Hva-debug.apk`** (autonome et auto-suffisant).
3. Installez directement sur votre smartphone ou tablette Android.

---

## 🛠️ Compilation & Tests en Ligne de Commande

```bash
# Exécuter l'ensemble des tests stricts
gradle testDebugUnitTest

# Compiler l'APK autonome
gradle assembleDebug
```
