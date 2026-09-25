// Inicialización de Firebase Admin (Firestore + FCM).
// Requiere la variable de entorno GOOGLE_APPLICATION_CREDENTIALS apuntando al JSON de
// service account, o correr en un entorno GCP con credenciales por defecto.
import admin from "firebase-admin";

if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.applicationDefault(),
  });
}

export const db = admin.firestore();
export const messaging = admin.messaging();
export const auth = admin.auth();
export default admin;
