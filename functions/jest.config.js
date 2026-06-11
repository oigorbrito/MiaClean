/**
 * Jest config for the Functions backend. Uses ts-jest so .ts sources can be exercised
 * without a separate compile step in CI.
 */
module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  roots: ["<rootDir>/src"],
  testMatch: ["**/__tests__/**/*.test.ts"],
  collectCoverageFrom: ["src/**/*.ts", "!src/index.ts", "!src/**/__tests__/**"],
  moduleFileExtensions: ["ts", "js", "json"],
  clearMocks: true,
};
