# PlateUp: Smart Campus Cafeteria Platform

PlateUp is a smart campus cafeteria ordering platform built for DAU (formerly DA-IICT). It streamlines the entire cafeteria workflow by connecting students, vendors, and the Cafeteria Management Committee (CMC) through a secure, real-time ordering and management system.

## 🚀 Features

### Student
- **Multi-Canteen Discovery**: Browse all campus canteens and their live status.
- **Smart Ordering**: Unified cart system with automatic packaging fee calculation.
- **Payments**: UPI payment workflow with support for Razorpay/Paytm payment gateway integration.
- **Live Tracking**: Visual progress from order placement to collection.
- **QR Pickup**: High-security verification for order collection.
- **Reviews & Ratings**: Share feedback on menu items.

### Vendor
- **Live Orders Dashboard**: Manage high-density traffic with a structured digital queue.
- **Menu Management**: Submit menu change requests for Admin approval.
- **Pickup Scanner**: Integrated QR and code scanner for fast verification.
- **Availability Toggle**: Real-time control over canteen and item status.

### Admin (CMC)
- **Canteen Onboarding**: Register and manage canteen vendors.
- **Governance**: Approve or reject menu change requests to maintain price stability.

## 🛠 Tech Stack

### Client
- **Language**: Kotlin
- **UI**: XML with Material Design 3
- **Scanner**: Google ML Kit

### Backend & Cloud
- **Database**: Firebase Realtime Database
- **Authentication**: Firebase Authentication (Google Sign-In)
- **Logic**: Firebase Cloud Functions (Node.js/TypeScript)

## 🏗 Architecture
```text
Android Client (Kotlin)
       ↓
Firebase (Realtime DB / Auth / Messaging)
       ↓
Cloud Functions (Server-side Validation / Payments)
```

## ⚙️ Setup Instructions

### 1. Prerequisites
- Android Studio Ladybug (or newer)
- A Firebase Project

### 2. Clone the Repository
```bash
git clone https://github.com/ossdaiict/PlateUp.git
cd PlateUp
```

### 3. Firebase Setup
1. Create a new project in the [Firebase Console](https://console.firebase.google.com/).
2. Enable **Authentication** (Google & Anonymous).
3. Enable **Realtime Database**.
4. Download `google-services.json` and place it in the `app/` directory.
5. Deploy security rules from `database.rules.json`.

### 4. Cloud Functions Setup
1. Navigate to the `functions/` directory.
2. Install dependencies: `npm install`.
3. Set secret for Admin Code:
   ```bash
   firebase functions:secrets:set ADMIN_CODE
   ```
4. Deploy functions: `firebase deploy --only functions`.

### 5. Build and Run
- Open the project in Android Studio.
- Sync Gradle.
- Run the `app` module on a device or emulator.

## 📁 Project Structure
- `app/`: Android client source code.
- `functions/`: Firebase Cloud Functions (TypeScript).
- `docs/`: Project documentation and reports.
- `database.rules.json`: Security rules for Realtime Database.

## 🔮 Future Improvements
- **Kotlin Multiplatform**: Support for cross-platform reliability and future iOS compatibility.
- **Advanced Analytics**: Detailed reporting for admin and vendor insights.
- **Additional Gateways**: Integration with more payment providers.

## 🤝 Credits
Developed for **DAU (formerly DA-IICT)** as a smart campus cafeteria management platform.
