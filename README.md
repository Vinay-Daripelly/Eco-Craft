# ♻️ Ecocraft – AI-Based Plastic Waste Upcycling App

**Ecocraft** is a mobile application that empowers individuals and organizations to manage plastic waste more effectively. Using machine learning, the app identifies the resin type of plastic waste and generates intelligent upcycling suggestions through LLM integration. It supports real-time tracking, visualization, and request management, promoting eco-friendly practices through technology.

---

## 🌟 Features

### 🔍 Plastic Detection
- Detects the **resin type** (e.g., PET, HDPE) from uploaded plastic images using a trained ML model.
- Classifies plastic based on visual features and material texture.

### 💡 Smart Upcycling Suggestions
- Integrates with a **Large Language Model (LLM)** (e.g., Gemini Pro) to suggest creative, quantity-based reuse ideas.
- Suggestions are categorized by **resin type** and **volume** (Small / Medium / Large).

### 📱 Dual Modes
- **Individual Mode**:  
  - Users upload plastic images and receive detection + suggestions.  
  - Displays daily summary of type, weight, and total plastic items submitted.

- **Organization Mode**:  
  - Visual dashboards with **bar and pie charts** showing plastic type counts and distributions.  
  - Supports tracking over time and across departments or events.

### 🔄 Recycle Request Management
- Admin dashboard to **approve** or **reject** recycle requests.
- On approval: Updates status and **decrements** plastic counts from Firestore.
- On rejection: Only updates status without data modification.

---

## 🔧 Tech Stack

- **Frontend**: Kotlin (Android Native)
- **Backend**: Firebase Firestore, Firebase Cloud Storage
- **AI/ML**:  
  - Custom ML model for plastic classification  
  - Gemini Pro API (LLM) for reuse suggestion generation
- **Charting**: MPAndroidChart or equivalent library
- **Authentication**: Firebase Auth (Email/Password)

---

