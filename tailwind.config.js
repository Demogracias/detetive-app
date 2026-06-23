/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{ts,tsx,html}',
    './android/app/src/main/assets/public/**/*.html'
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        noir: {
          bg: '#0a0a0a',
          surface: '#111111',
          border: '#1a1a1a',
          fg: '#e8e8e8',
          muted: '#666666',
          accent: '#00ff41',
          accentDim: '#00cc33',
          warn: '#dc2626',
          gold: '#ffb000',
          whiskey: '#d4a574'
        }
      },
      fontFamily: {
        mono: ['JetBrains Mono', 'Fira Code', 'Courier New', 'monospace'],
        deck: ['Courier Prime', 'Courier New', 'monospace'],
        display: ['Space Grotesk', 'sans-serif']
      },
      spacing: {
        '18': '4.5rem',
        '88': '22rem',
        '128': '32rem'
      },
      boxShadow: {
        noir: '4px 4px 0px 0px #000000',
        'noir-lg': '8px 8px 0px 0px #000000'
      },
      animation: {
        'cursor-blink': 'cursor-blink 1s step-end infinite',
        'scanline': 'scanline 8s linear infinite',
        'fade-in': 'fade-in 0.3s ease-out',
        'slide-up': 'slide-up 0.4s ease-out'
      },
      keyframes: {
        'cursor-blink': {
          '0%, 50%': { opacity: '1' },
          '51%, 100%': { opacity: '0' }
        },
        'scanline': {
          '0%': { transform: 'translateY(-100%)' },
          '100%': { transform: 'translateY(100%)' }
        },
        'fade-in': {
          '0%': { opacity: '0', transform: 'translateY(10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' }
        },
        'slide-up': {
          '0%': { opacity: '0', transform: 'translateY(20px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' }
        }
      }
    }
  },
  plugins: []
};