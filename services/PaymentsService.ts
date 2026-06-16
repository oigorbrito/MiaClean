export class PaymentsService {
  async processPayment(amount: number) {
    console.log(`Processing payment of ${amount}`);
    return true;
  }
}

// FIX: passing a number as expected by the method signature
