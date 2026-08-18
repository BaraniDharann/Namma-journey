import js from '@eslint/js'
import globals from 'globals'
import react from 'eslint-plugin-react'
import reactHooks from 'eslint-plugin-react-hooks'

// Flat config (ESLint 9). Scoped to catch the mistakes that survive a passing build —
// unused bindings, swallowed errors, hooks with missing dependencies. Stylistic rules are
// left off so the config stays useful without reformatting the whole tree.
export default [
  { ignores: ['dist/**', 'build/**', 'node_modules/**', 'public/**', 'scripts/**'] },
  js.configs.recommended,

  // Build tooling runs under Node, not the browser.
  {
    files: ['*.config.js', 'vite.config.js'],
    languageOptions: { globals: { ...globals.node } },
  },

  {
    files: ['src/**/*.{js,jsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: { ...globals.browser, ...globals.es2021 },
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    settings: { react: { version: 'detect' } },
    plugins: { react, 'react-hooks': reactHooks },
    rules: {
      ...react.configs.flat.recommended.rules,
      ...reactHooks.configs.recommended.rules,

      // The project uses the automatic JSX runtime, so React need not be in scope.
      'react/react-in-jsx-scope': 'off',
      // Prop types are not used anywhere in this codebase; flagging them would be noise.
      'react/prop-types': 'off',
      'no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],

      // eslint-plugin-react-hooks v6 ships the React Compiler rules. They describe patterns
      // the compiler cannot optimise rather than behaviour that is wrong, and satisfying them
      // means restructuring components that work and are covered by the E2E suite. Kept as
      // warnings so they are visible to anyone tightening this up, without failing CI over
      // code that is merely un-optimisable.
      'react-hooks/set-state-in-effect': 'warn',
      'react-hooks/immutability': 'warn',
      'react-hooks/preserve-manual-memoization': 'warn',
      'react-hooks/refs': 'warn',
    },
  },
]
