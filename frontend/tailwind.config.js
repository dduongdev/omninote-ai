/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        surface: {
          DEFAULT: '#1e1e2e',
          raised: '#252535',
          overlay: '#2a2a3d',
        },
        brand: {
          DEFAULT: '#7c6af7',
          hover: '#6b58f0',
          light: '#9d8fff',
        },
      },
    },
  },
  plugins: [],
}
