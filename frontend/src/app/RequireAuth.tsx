import { Navigate, Outlet } from 'react-router-dom'
import { getToken } from '../shared/api/client'

function RequireAuth() {
  if (!getToken()) {
    return <Navigate to="/app/login" replace />
  }
  return <Outlet />
}

export default RequireAuth
