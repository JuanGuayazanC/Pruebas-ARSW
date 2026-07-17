import http from 'k6/http';
import { sleep, check } from 'k6';

export const options = {
  vus: 10,
  duration: '30s',
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const orderId = __ENV.ORDER_ID || 'ORD-1';

export default function () {
  const response = http.get(`${baseUrl}/orders/${orderId}`);

  check(response, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });

  sleep(1);
}
