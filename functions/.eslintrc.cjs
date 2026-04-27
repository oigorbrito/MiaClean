/**
 * ESLint config for the Functions backend. Strict-but-pragmatic defaults: catches the obvious
 * bugs (unused promises, implicit any) without forcing stylistic noise that prettier already
 * settles. Test files relax the no-explicit-any rule because Jest mocks routinely use `any`.
 */
module.exports = {
  root: true,
  env: {
    node: true,
    es2022: true,
    jest: true,
  },
  parser: "@typescript-eslint/parser",
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: "module",
    project: ["tsconfig.eslint.json"],
    tsconfigRootDir: __dirname,
  },
  plugins: ["@typescript-eslint", "import"],
  extends: [
    "eslint:recommended",
    "plugin:@typescript-eslint/recommended",
    "plugin:import/recommended",
    "plugin:import/typescript",
    "prettier",
  ],
  settings: {
    "import/resolver": {
      typescript: {
        project: "tsconfig.eslint.json",
      },
      node: {
        extensions: [".ts", ".js", ".cjs", ".mjs"],
      },
    },
  },
  rules: {
    "@typescript-eslint/no-explicit-any": "warn",
    "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_" }],
    "@typescript-eslint/no-var-requires": "off",
    "@typescript-eslint/no-require-imports": "off",
    "import/no-unresolved": [
      "error",
      {
        // firebase-functions/params, firebase-admin/app, firebase-functions/v2/https etc. are
        // exposed via package "exports" maps that eslint-plugin-import's resolver doesn't fully
        // understand. The TypeScript compiler resolves them correctly (build is clean), so we
        // ignore the false positives here rather than disabling the rule globally.
        ignore: ["^firebase-functions(/.*)?$", "^firebase-admin(/.*)?$"],
      },
    ],
    "import/order": [
      "warn",
      {
        groups: ["builtin", "external", "internal", "parent", "sibling", "index"],
        "newlines-between": "always",
        alphabetize: { order: "asc", caseInsensitive: true },
      },
    ],
    "no-console": "off",
  },
  ignorePatterns: ["lib/", "node_modules/", "coverage/"],
  overrides: [
    {
      files: ["**/__tests__/**/*.ts", "**/*.test.ts"],
      rules: {
        "@typescript-eslint/no-explicit-any": "off",
      },
    },
  ],
};
