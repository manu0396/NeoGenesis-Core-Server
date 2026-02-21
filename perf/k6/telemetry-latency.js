import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  scenarios: {
    telemetry_eval: {
      executor: "constant-arrival-rate",
      rate: 100,
      timeUnit: "1s",
      duration: "3m",
      preAllocatedVUs: 50,
      maxVUs: 200
    }
  },
  thresholds: {
    http_req_duration: ["p(95)<50", "p(99)<50"],
    checks: ["rate>0.99"]
  }
};

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const jwt = __ENV.JWT || "";

export default function () {
  const payload = JSON.stringify({
    printer_id: "hil-printer-01",
    timestamp_ms: Date.now(),
    nozzle_temp_celsius: 36.9,
    extrusion_pressure_kpa: 108.4,
    cell_viability_index: 0.96,
    encrypted_image_matrix_base64: "",
    bio_ink_viscosity_index: 0.71,
    bio_ink_ph: 7.34,
    nir_ii_temp_celsius: 37.0,
    morphological_defect_probability: 0.03,
    print_job_id: "job-hil-01",
    tissue_type: "retina"
  });

  const headers = {
    "Content-Type": "application/json",
    "Idempotency-Key": `${__VU}-${__ITER}-${Date.now()}`
  };
  if (jwt) {
    headers["Authorization"] = `Bearer ${jwt}`;
  }

  const res = http.post(`${baseUrl}/telemetry/evaluate`, payload, { headers });
  check(res, {
    "status is 200": (r) => r.status === 200
  });
  sleep(0.01);
}
