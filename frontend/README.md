# Yusi Frontend - 灵魂叙事

A modern React frontend application for Yusi - an AI-powered social platform that creates "情景室" (Situation Rooms) for personality analysis and encrypted diary features.

## 🚀 Technology Stack

- **Framework**: React 19 with TypeScript
- **Build Tool**: Vite with Rolldown (next-generation bundler)
- **Styling**: Tailwind CSS with PostCSS
- **State Management**: Zustand
- **Routing**: React Router v6
- **UI Components**: Custom components with Radix UI primitives
- **HTTP Client**: Axios with interceptors
- **Notifications**: Sonner (toast notifications)
- **Icons**: Lucide React

## 🛠️ Development Setup

### Prerequisites

- Node.js 18+ 
- npm or pnpm

### Installation

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

### Environment Configuration

Create a `.env` file in the root directory:

```env
VITE_API_BASE=http://localhost:20611
```

## 📁 Project Structure

```
src/
├── components/          # Reusable UI components
│   ├── ui/             # Base UI components (Button, Input, Textarea, etc.)
│   ├── room/           # Room-specific components
│   └── Diary.tsx       # Diary component
├── pages/               # Page components
│   ├── Home.tsx        # Landing page
│   ├── Room.tsx        # Dynamic room page
│   └── Diary.tsx       # Diary page
├── lib/                 # Utility libraries and API functions
│   ├── api.ts          # Axios instance with interceptors
│   ├── room.ts         # Room API functions and types
│   └── index.ts        # Barrel exports
├── utils/               # Utility functions
│   └── index.ts        # Character counting and other utilities
├── hooks/               # Custom React hooks
├── stores/               # Zustand state management
└── main.tsx             # Application entry point
```

## 🎯 Features

### 1. 情景室 (Situation Room)
- **Room Creation**: Create a room with custom member limit (2-8 people)
- **Room Joining**: Join existing rooms using invitation codes
- **Narrative Submission**: Submit personal narratives with 1000-character limit
- **AI Analysis**: Real-time personality analysis and compatibility reports
- **Room Status**: Visual indicators for room states (Waiting, In Progress, Completed)

### 2. AI Diary
- **Encrypted Storage**: Secure diary entries with AES/GCM encryption
- **Rich Text Editor**: Full-featured text area for diary writing
- **Privacy Protection**: Local user ID management for data isolation

### 3. Modern UI/UX
- **Responsive Design**: Mobile-first approach with Tailwind CSS
- **Loading States**: Skeleton screens and loading indicators
- **Error Handling**: Toast notifications for user feedback
- **Character Counting**: Real-time character limit feedback

## 🔧 API Integration

The frontend communicates with the backend through a RESTful API:

- **Base URL**: Configurable via environment variables
- **Error Handling**: Global axios interceptors for consistent error messaging
- **Endpoints**:
  - `POST /api/rooms` - Create new room
  - `POST /api/rooms/{code}/join` - Join existing room
  - `POST /api/rooms/{code}/submit` - Submit narrative
  - `GET /api/rooms/{code}/report` - Get AI analysis report

## 🚀 Build Configuration

### Vite Configuration
- **Proxy Setup**: `/api` routes proxied to backend (port 20611)
- **Development Server**: Port 5174 with hot module replacement
- **Production Build**: Optimized bundle with code splitting

### TypeScript Configuration
- **Strict Mode**: Enabled for type safety
- **Path Aliases**: `@` points to `src` directory
- **Modern Target**: ES2020+ features

### PostCSS Configuration
- **Tailwind CSS**: Using new `@tailwindcss/postcss` plugin
- **Autoprefixer**: Automatic vendor prefixing

## 🎨 Styling Guidelines

- **Tailwind CSS**: Utility-first approach
- **Color Palette**: Modern, clean design with consistent spacing
- **Responsive Breakpoints**: Mobile-first design strategy
- **Component Architecture**: Small, focused components (< 200 lines)

## 🔒 Security Features

- **Local Storage**: User ID stored locally for session management
- **API Communication**: Secure HTTP-only cookies for authentication
- **Input Validation**: Client-side validation with server-side verification

## 📱 Browser Support

- **Modern Browsers**: Chrome, Firefox, Safari, Edge (latest versions)
- **ES2020+ Features**: Async/await, optional chaining, nullish coalescing
- **CSS Grid/Flexbox**: Modern layout techniques

## 🧪 Development Commands

```bash
# Development with hot reload
npm run dev

# Type checking
npm run check

# Production build
npm run build

# Preview production build
npm run preview

# Linting (if configured)
npm run lint
```

## 🤝 Contributing

1. Follow the existing code style and conventions
2. Keep components small and focused
3. Use TypeScript for type safety
4. Test your changes thoroughly
5. Update documentation as needed

## 📄 License

This project is part of the Yusi platform and follows the same licensing terms.
