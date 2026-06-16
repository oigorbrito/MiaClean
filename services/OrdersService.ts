export class OrdersService {
  async getOrders(patientId: string) {
    return [`Order for ${patientId}`];
  }
}

// FIX: passing a string as expected by the method signature
