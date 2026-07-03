import { Routes, Route, Navigate } from 'react-router-dom'
import LoginPage from './pages/login-page'
import TransfersListPage from './pages/transfers-list-page'
import CreateTransferPage from './pages/create-transfer-page'
import TransferDetailPage from './pages/transfer-detail-page'
import RequireAuth from './components/require-auth'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/transfers"
        element={
          <RequireAuth>
            <TransfersListPage />
          </RequireAuth>
        }
      />
      <Route
        path="/transfers/new"
        element={
          <RequireAuth>
            <CreateTransferPage />
          </RequireAuth>
        }
      />
      <Route
        path="/transfers/:id"
        element={
          <RequireAuth>
            <TransferDetailPage />
          </RequireAuth>
        }
      />
      <Route path="/" element={<Navigate to="/transfers" replace />} />
      <Route path="*" element={<Navigate to="/transfers" replace />} />
    </Routes>
  )
}
