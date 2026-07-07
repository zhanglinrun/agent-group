import '@ant-design/v5-patch-for-react-19'
import { createRoot } from 'react-dom/client'
import './reactor-ui/global.css'
import './workspace-chrome.css'
import App from './reactor-ui/App.tsx'

const root = document.getElementById('root')

if (root) {
  createRoot(root).render(
    <App />
  )
} else {
  console.error('Root element not found')
}
