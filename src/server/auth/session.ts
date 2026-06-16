import { Request } from 'express';

const DEMO_PATIENT_ID = process.env.DEMO_PATIENT_ID || 'demo-patient-123';

export const getSession = (req: Request) => {
  if (!req.session) {
    if (process.env.NODE_ENV === 'production') {
      throw new Error("Unauthorized: Active session required in production.");
    }

    // In development/test, we might allow a controlled demo patient if explicitly configured
    if (process.env.USE_DEMO === 'true') {
      return { patientId: DEMO_PATIENT_ID };
    }
  }

  return req.session;
};
