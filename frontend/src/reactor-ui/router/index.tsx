import React, { Suspense } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import Layout from '@/layout/index';
import { Loading } from '@/components';
import { ROUTES } from './routes';

// 使用 React.lazy 懒加载组件
const Home = React.lazy(() => import('@/pages/Home'));
const WorkspaceMRag = React.lazy(() => import('@/pages/WorkspaceMRag'));
const WorkspaceImageGeneration = React.lazy(() => import('@/pages/WorkspaceImageGeneration'));
const WorkspaceTrade = React.lazy(() => import('@/pages/WorkspaceTrade'));
const NotFound = React.lazy(() => import('@/components/NotFound'));
const AdminDashboard = React.lazy(() => import('../../components/AdminDashboard'));

// 创建路由配置
const router = createBrowserRouter([
  {
    path: ROUTES.ADMIN,
    element: (
      <Suspense fallback={<Loading loading={true} className="h-full"/>}>
        <AdminDashboard />
      </Suspense>
    ),
  },
  {
    path: ROUTES.HOME,
    element: <Layout />,
    children: [
      {
        index: true,
        element: (
          <Suspense fallback={<Loading loading={true} className="h-full"/>}>
            <Home />
          </Suspense>
        ),
      },
      {
        path: ROUTES.WORKSPACE,
        element: <Navigate to={ROUTES.WORKSPACE_MRAG} replace />,
      },
      {
        path: ROUTES.WORKSPACE_IMAGE_LEGACY,
        element: <Navigate to={ROUTES.WORKSPACE_IMAGE_GENERATION} replace />,
      },
      {
        path: ROUTES.WORKSPACE_MRAG,
        element: (
          <Suspense fallback={<Loading loading={true} className="h-full"/>}>
            <WorkspaceMRag />
          </Suspense>
        ),
      },
      {
        path: ROUTES.WORKSPACE_IMAGE_GENERATION,
        element: (
          <Suspense fallback={<Loading loading={true} className="h-full"/>}>
            <WorkspaceImageGeneration />
          </Suspense>
        ),
      },
      {
        path: ROUTES.WORKSPACE_TRADE,
        element: (
          <Suspense fallback={<Loading loading={true} className="h-full"/>}>
            <WorkspaceTrade />
          </Suspense>
        ),
      },
      {
        path: ROUTES.NOT_FOUND,
        element: (
          <Suspense fallback={<Loading loading={true} className="h-full"/>}>
            <NotFound />
          </Suspense>
        ),
      },
    ],
  },
  // 重定向所有未匹配的路由到 404 页面
  {
    path: '*',
    element: <Navigate to={ROUTES.NOT_FOUND} replace />,
  },
]);

export default router;
