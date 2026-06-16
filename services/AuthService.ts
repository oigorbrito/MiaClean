export class AuthService {
  static async signIn(credentials: any) {
    // Isolate demo bypass strictly for non-production environments
    if (process.env.NODE_ENV !== 'production' && process.env.USE_DEMO === 'true' && credentials.isDemo) {
      return { id: process.env.DEMO_PATIENT_ID || 'dev-demo-id', role: 'patient' };
    }

    // Lógica de autenticação real (mock)
    console.log("Real auth attempt for", credentials.username);
    return null;
  }
}
