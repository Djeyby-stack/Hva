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
  - Écho instantané des caractères tapés à l'écran en temps réel.
  - Gestion native des touches Retour arrière (`Backspace`/`DEL`), suppression avant (`Suppr`), et flèches de curseur (`←` / `→`).
  - Historique des commandes avec navigation fluide (`↑` / `↓`).
  - Primitives intégrées ultra-rapides (`cd`, `pwd`, `clear`, `export`, `history`, `help`, `exit`, `hva-info`).
  - Exécution directe et asynchrone des commandes système (`ls`, `uname`, `ps`, `cat`, `echo`, etc.).
- 🔔 **Système de Notification en arrière-plan (`HvaNotificationManager`)** :
  - Notification persistante dans la barre d'état Android indiquant le nombre de sessions actives, le titre de la console et le PID en cours.
  - Clic direct sur la notification pour réouvrir ou basculer sur l'application.
  - Demande automatique de la permission `POST_NOTIFICATIONS` sur Android 13+ (API 33+).
- 📋 **Support Copier / Coller avancé** :
  - Touches dédiées **`COPY`**, **`PASTE`**, **`SEL`** dans la barre de touches rapides.
  - Sélection par glissement tactile sur l'écran du terminal.
  - Actions rapides "Copier tout le terminal" et "Coller" dans le tiroir latéral.
- ⌨️ **Barre de touches rapides multi-pages (2 Pages)** :
  - **Page 1 (Contrôle & Navigation)** : `ESC`, `☰`, `↕`, `HOME`, `↑`, `END`, `⇄` (TAB), `CTRL`, `ALT`, `←`, `↓`, `→`, `PASTE`.
  - **Page 2 (Symboles & Presse-papier)** : `/`, `-`, `~`, `|`, `$`, `&`, `COPY`, `PASTE`, `SEL`, `PGUP`, `PGDN`, `;`, `:`.

---

## 📱 Téléchargement de l'APK (v0.0.4)

1. Rendez-vous dans la section [**Releases**](https://github.com/Djeyby-stack/Hva/releases) du dépôt.
2. Téléchargez **`Hva-debug.apk`** sous la version **v0.0.4**.
3. Installez l'APK sur votre appareil Android.

---

## 🧪 Guide de Test & Vérification (v0.0.4)

### 1. Test de la saisie au clavier et affichage direct
1. Ouvrez l'application. Le curseur clignotant vert s'affiche avec le prompt `hva:~$ `.
2. Tapez n'importe quel texte ou commande (ex: `echo "Bonjour Hva"`).
3. **Vérification** : Chaque caractère s'affiche immédiatement au fur et à mesure de la frappe.
4. Appuyez sur **`Entrée`** : La commande s'exécute et affiche le résultat instantanément.

### 2. Test du Copier / Coller
- **Coller depuis le presse-papier** :
  1. Copiez n'importe quel texte dans une autre application.
  2. Dans Hva, touchez le bouton **`PASTE`** dans la barre de touches du bas.
  3. Le texte est immédiatement inséré dans la ligne de commande.
- **Sélectionner et Copier** :
  1. Glissez le doigt sur du texte à l'écran pour sélectionner une zone, ou touchez **`SEL`** (Page 2 de la barre d'outils) pour tout sélectionner.
  2. Touchez **`COPY`** : Un message de confirmation confirme que le texte est dans le presse-papier.
  3. Collez-le où vous le souhaitez.

### 3. Test de la notification Android
1. Lors du premier lancement, autorisez les notifications si la boîte de dialogue s'affiche (Android 13+).
2. Abaissez la barre des notifications d'Android.
3. **Vérification** : La notification **Hva Terminal** apparaît avec l'icône, le nombre de sessions actives et le PID.
4. Cliquez sur la notification : l'application s'ouvre directement.

### 4. Test des commandes interactives
```bash
# Vérification de l'environnement et de la version
hva-info

# Navigation dans les dossiers
cd ..
pwd
cd ~
pwd

# Liste des fichiers et variables
ls -la
echo $PATH

# Historique des commandes
history
```

---

## 🛠️ Compilation en local

```bash
# Compiler l'APK de débogage
gradle assembleDebug

# L'APK compilé sera généré dans :
# app/build/outputs/apk/debug/app-debug.apk
```

