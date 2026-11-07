import { lazy, Suspense } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { BrowserRouter, NavLink, Route, Routes } from "react-router-dom";
import { api } from "./api";
import { PlayerSheetProvider } from "./components/PlayerSheet";
import { IconGrid, IconHome, IconList, IconLive, IconUser } from "./components/Icons";
import { Skeleton } from "./components/bits";
import { Home } from "./pages/Home";
import { Login } from "./pages/Login";

// Secondary screens load on demand to keep the first paint small.
const League = lazy(() => import("./pages/League").then((m) => ({ default: m.League })));
const Rankings = lazy(() => import("./pages/Rankings").then((m) => ({ default: m.Rankings })));
const Trade = lazy(() => import("./pages/Trade").then((m) => ({ default: m.Trade })));
const Accounts = lazy(() => import("./pages/Accounts").then((m) => ({ default: m.Accounts })));
const Live = lazy(() => import("./pages/Live").then((m) => ({ default: m.Live })));
const tools = () => import("./pages/Tools");
const ToolsHome = lazy(() => tools().then((m) => ({ default: m.ToolsHome })));
const OptimizeAll = lazy(() => tools().then((m) => ({ default: m.OptimizeAll })));
const Exposure = lazy(() => tools().then((m) => ({ default: m.Exposure })));
const News = lazy(() => tools().then((m) => ({ default: m.News })));
const leagueTools = () => import("./pages/LeagueTools");
const Waivers = lazy(() => leagueTools().then((m) => ({ default: m.Waivers })));
const Power = lazy(() => leagueTools().then((m) => ({ default: m.Power })));
const TradeFinder = lazy(() => leagueTools().then((m) => ({ default: m.TradeFinder })));
const StartSit = lazy(() => import("./pages/StartSit").then((m) => ({ default: m.StartSit })));
const Draft = lazy(() => import("./pages/Draft").then((m) => ({ default: m.Draft })));

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
              <Route path="/live" element={<Live />} />
              <Route path="/tools" element={<ToolsHome />} />
              <Route path="/tools/optimize" element={<OptimizeAll />} />
              <Route path="/tools/exposure" element={<Exposure />} />
              <Route path="/tools/news" element={<News />} />
              <Route path="/tools/waivers" element={<Waivers />} />
              <Route path="/tools/power" element={<Power />} />
              <Route path="/tools/trade-finder" element={<TradeFinder />} />
              <Route path="/tools/start-sit" element={<StartSit />} />
              <Route path="/tools/draft" element={<Draft />} />
              <Route path="/accounts" element={<Accounts passwordRequired={!!session.data?.passwordRequired} onLogout={() => qc.invalidateQueries()} />} />
            </Routes>
          </Suspense>
        </div>
        <div className="tabbar">
          <nav>
            <NavLink to="/" end><IconHome />Teams</NavLink>
            <NavLink to="/live"><IconLive />Live</NavLink>
            <NavLink to="/rankings"><IconList />Rankings</NavLink>
            <NavLink to="/tools"><IconGrid />Tools</NavLink>
            <NavLink to="/accounts"><IconUser />Accounts</NavLink>
          </nav>
        </div>
      </PlayerSheetProvider>
    </BrowserRouter>
  );
}
