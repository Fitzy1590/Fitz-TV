import { lazy, Suspense } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { BrowserRouter, NavLink, Route, Routes } from "react-router-dom";
import { api } from "./api";
import { PlayerSheetProvider } from "./components/PlayerSheet";
import { IconHome, IconList, IconSwap, IconUser } from "./components/Icons";
import { Skeleton } from "./components/bits";
import { Home } from "./pages/Home";
import { Login } from "./pages/Login";

// Secondary screens load on demand to keep the first paint small.
const League = lazy(() => import("./pages/League").then((m) => ({ default: m.League })));
const Rankings = lazy(() => import("./pages/Rankings").then((m) => ({ default: m.Rankings })));
const Trade = lazy(() => import("./pages/Trade").then((m) => ({ default: m.Trade })));
const Accounts = lazy(() => import("./pages/Accounts").then((m) => ({ default: m.Accounts })));

export function App() {
  const qc = useQueryClient();
  const session = useQuery({ queryKey: ["session"], queryFn: api.session, staleTime: Infinity });
  if (session.isLoading) return null;
  if (session.data && !session.data.authed) return <Login onDone={() => qc.invalidateQueries()} />;

  return (
    <BrowserRouter>
      <PlayerSheetProvider>
        <div className="app">
          <Suspense fallback={<div className="page" style={{ paddingTop: 80 }}><Skeleton h={70} n={5} /></div>}>
            <Routes>
              <Route path="/" element={<Home />} />
              <Route path="/league/:key" element={<League />} />
              <Route path="/rankings" element={<Rankings />} />
              <Route path="/trade" element={<Trade />} />
              <Route path="/accounts" element={<Accounts passwordRequired={!!session.data?.passwordRequired} onLogout={() => qc.invalidateQueries()} />} />
            </Routes>
          </Suspense>
        </div>
        <div className="tabbar">
          <nav>
            <NavLink to="/" end><IconHome />Teams</NavLink>
            <NavLink to="/rankings"><IconList />Rankings</NavLink>
            <NavLink to="/trade"><IconSwap />Trade</NavLink>
            <NavLink to="/accounts"><IconUser />Accounts</NavLink>
          </nav>
        </div>
      </PlayerSheetProvider>
    </BrowserRouter>
  );
}
