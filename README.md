# Hva — Terminal Android Moderne & Autonome

[![Build & Release Android Debug APK](https://github.com/Djeyby-stack/Hva/actions/workflows/build-and-release-apk.yml/badge.svg)](https://github.com/Djeyby-stack/Hva/actions/workflows/build-and-release-apk.yml)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android&logoColor=white)](https://android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)

**Hva** est un émulateur de terminal Android autonome, rapide et moderne conçu avec **Jetpack Compose** et **Kotlin Coroutines**. Il fournit un environnement UNIX complet (Bionic / BusyBox) sans nécessiter de root, une barre de touches rapides, la gestion de sessions multiples et un gestionnaire de paquets intégré (`hva pkg`).

---

## 🌟 Fonctionnalités principales

- 💻 **Émulateur de Terminal VT100 / ANSI / xterm-256color** : Rendu textuel fluide, support des couleurs ANSI et codes d'échappement pour les shells interactifs.
- ⚡ **Environnement UNIX Natif** : Accès direct au shell Bionic Android (`/system/bin/sh`) avec environnement configuré (`$HOME`, `$PREFIX`, `$PATH`, `$TMPDIR`).
- 📦 **Gestionnaire de paquets (`hva pkg` & `pkg`)** : Commandes `pkg search`, `pkg install`, `pkg upgrade` et outil de diagnostic système `hva doctor`.
- 🗂️ **Multi-Sessions** : Gestion de plusieurs consoles en parallèle avec tiroir de navigation latéral ergonomique.
- ⌨️ **Barre de touches rapides (Extra Keys)** : Boutons dédiés pour `ESC`, `TAB`, `CTRL`, `ALT`, `▲`, `▼`, `◄`, `►`, `|`, `/`, `-`, `~`.
- 📋 **Actions Presse-Papier & Menu contextuel** : Copier, coller, tout sélectionner et contrôle du clavier virtuel en un clic.
- 🚀 **Intégration Continue (GitHub Actions)** : Compilation et publication automatique de l'APK à chaque push et release.

---

## 📱 Téléchargement de l'APK

1. Rendez-vous dans la section [**Releases**](https://github.com/Djeyby-stack/Hva/releases) du dépôt.
2. Téléchargez **`Hva-debug.apk`** sous la section **Assets** (Actifs).
3. Installez l'APK sur votre appareil Android (autoriser l'installation depuis cette source si demandé).

---

## 🧪 Guide de Test & Vérification

Suivez ces étapes pour vérifier que tout fonctionne parfaitement :

### 1. Test des commandes UNIX de base
Dans le terminal, tapez les commandes suivantes et validez avec `Entrée` :
```bash
# Vérifier le répertoire courant
pwd

# Lister les fichiers et variables d'environnement
ls -la
echo $HOME
echo $PREFIX

# Afficher les informations système
uname -a
id
```

### 2. Test des outils Hva & Gestionnaire de paquets
```bash
# Diagnostic complet de l'environnement Hva
hva doctor

# Aide sur les commandes Hva
hva help

# Recherche et installation de paquets
pkg search curl
pkg install curl
pkg list
```

### 3. Test de la barre de touches rapides (Extra Keys)
1. Tapez `ec` puis touchez la touche **`TAB`** dans la barre en bas pour tester l'autocomplétion (`echo`).
2. Tapez une commande en boucle ou un texte long et touchez **`CTRL`** puis **`C`** pour interrompre le processus.
3. Utilisez les flèches **`▲`** et **`▼`** pour parcourir l'historique des commandes.

### 4. Test du gestionnaire multi-sessions
1. Faites glisser le doigt depuis le bord gauche ou cliquez sur l'icône de tiroir en haut à gauche.
2. Cliquez sur **`+ Nouvelle session`** pour lancer un second terminal indépendant.
3. Exécutez des commandes différentes dans chaque session et basculez entre elles via le tiroir.
4. Cliquez sur **`Fermer la session`** pour quitter une session.

### 5. Test du presse-papier
1. Effectuez un appui long sur l'écran du terminal.
2. Utilisez **`Tout sélectionner`** puis **`Copier`**.
3. Testez le bouton **`Coller`** dans une nouvelle invite de commande.

---

## 🛠️ Compilation en local

### Prérequis
- **JDK 17** ou supérieur
- **Android SDK** (API 34 / 35 / 36)
- **Gradle 9.3.1** (ou Gradle Wrapper)

### Commandes de build

```bash
# Exécuter les tests unitaires et Robolectric
gradle :app:testDebugUnitTest

# Compiler l'APK de débogage
gradle assembleDebug

# L'APK compilé sera généré dans :
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 🏗️ Architecture du Projet

```
Hva/
├── .github/workflows/
│   └── build-and-release-apk.yml   # Workflow CI/CD automatique
├── app/
│   ├── src/main/
│   │   ├── java/com/example/hva/
│   │   │   ├── runtime/            # Gestionnaire de processus PTY & UNIX (HvaEnvironment)
│   │   │   ├── terminal/           # Émulateur VT100, buffer d'écran & parser ANSI
│   │   │   ├── session/            # Gestionnaire de sessions multiples (SessionManager)
│   │   │   ├── ui/                 # Écrans Jetpack Compose, Vue Terminal & Clavier virtuel
│   │   │   └── MainActivity.kt     # Point d'entrée de l'application
│   │   └── res/                    # Ressources, chaînes et icônes
│   └── build.gradle.kts            # Dépendances et configuration Android
├── gradle/                         # Version catalog et configuration Gradle
└── README.md                       # Documentation du projet
```

---

## 📄 Licence
Ce projet est développé et distribué sous licence open-source.
