import { Request } from 'express';

const DEMO_PATIENT_ID = process.env.DEMO_PATIENT_ID || 'demo-patient-123';

export const getSession = (req: Request) => {
  // Hard enforcement: Production MUST have an active session.
  // No demo fallbacks are allowed regardless of flags.
  if (process.env.NODE_ENV === 'production') {
    if (!req.session) {
      throw new Error("Unauthorized: Active session required in production.");
    }
    return req.session;
  }

  // In non-production environments, fallback to demo patient if explicitly requested.
  if (!req.session && process.env.USE_DEMO === 'true') {
    return { patientId: DEMO_PATIENT_ID };
  }

  return req.session;
};
