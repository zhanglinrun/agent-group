export const APP_ROUTES = {
  admin: "/admin",
  agent: "/",
  workspaceImage: "/workspace/image",
  workspaceData: "/workspace/data",
  workspaceMrag: "/workspace/mrag",
  workspaceTrade: "/workspace/trade"
} as const;

export const WORKSPACE_ROUTES = [
  APP_ROUTES.agent,
  APP_ROUTES.workspaceImage
] as const;

export const INTERNAL_WORKSPACE_ROUTES = [
  ...WORKSPACE_ROUTES,
  APP_ROUTES.workspaceData,
  APP_ROUTES.workspaceMrag,
  APP_ROUTES.workspaceTrade
] as const;

export function isAdminRoute(pathname: string): boolean {
  const path = String(pathname || "/").replace(/\/+$/, "") || "/";
  return path === APP_ROUTES.admin;
}
