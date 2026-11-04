/* bili-duo 交互原型 — 纯前端状态机，无依赖 */
(() => {
  'use strict';

  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];

  const app = $('#app');
  const browseShell = $('#browseShell');
  const screenHome = $('#screenHome');
  const screenPlayer = $('#screenPlayer');
  const topIdle = $('#topIdle');
  const topVideo = $('#topVideo');
  const danmakuLayer = $('#danmakuLayer');
  const overlayHost = $('#overlayHost');
  const bottomScreen = $('#bottomScreen');
  const modeDot = $('#modeDot');
  const modeLabel = $('#modeLabel');
  const modeMid = $('#modeMid');
  const modeRight = $('#modeRight');
  const guideModeDot = $('#guideModeDot');
  const guideMode = $('#guideMode');
  const guideHint = $('#guideHint');
  const hud = $('#hud');
  const toastEl = $('#toast');

  const MODE_INFO = {
    browse: { dot: 'gray', label: '浏览', mid: 'B 返回 · A 打开', right: '←→ 移动',
      hint: '方向键移动焦点<br>Enter 打开' },
    topPlayer: { dot: 'pink', label: '手柄：播放', mid: 'Y 切下屏 · A 暂停', right: 'B 退出 · X 弹幕',
      hint: 'A 暂停 · ←→ 快进<br>↑↓ 音量 · Y 切下屏' },
    bottomUi: { dot: 'blue', label: '手柄：下屏', mid: 'B 返回 · Y 回播放', right: 'A 确认 · START 菜单',
      hint: '方向键移动焦点<br>A 确认 · B 返回' },
    overlay: { dot: 'yellow', label: '菜单', mid: 'A 确认', right: 'B 关闭',
      hint: '方向键选择<br>A 确认 · B 关闭' },
  };

  const PARTS = {
    1: { title: '双屏掌机上的 B 站客户端', duration: '12:34' },
    2: { title: '安装与初始配置', duration: '08:42' },
    3: { title: '焦点与输入路由', duration: '15:20' },
    4: { title: '真机验收清单', duration: '11:05' },
  };

  const state = {
    screen: 'home',
    mode: 'browse',
    overlay: null,
    overlayReturnId: null,
    videoStarted: false,
    playing: true,
    position: 201,
    duration: 754,
    part: 1,
    quality: '1080P',
    speed: 1,
    volume: 80,
    danmaku: true,
    following: false,
    faved: false,
    playerTab: 'comments',
    focusEl: null,
    focusMemory: {},
    scale: 0.5,
    swallowClick: false,
    swallowTimer: 0,
    hudTimer: 0,
    toastTimer: 0,
    tickTimer: 0,
  };

  /* ---------------- helpers ---------------- */

  const isVisible = (el) => !!el && el.offsetParent !== null && el.getClientRects().length > 0;

  function focusScope() {
    if (state.overlay) return $(`[data-overlay="${state.overlay}"]`, overlayHost);
    if (state.screen === 'player') return screenPlayer;
    return browseShell;
  }

  function focusablesIn(scope) {
    if (!scope) return [];
    return $$('[data-focusable]', scope).filter(isVisible);
  }

  function scopeKey() {
    return state.overlay ? 'overlay:' + state.overlay : state.screen;
  }

  function ensureVisible(el) {
    let p = el.parentElement;
    while (p && p !== document.body) {
      const cs = getComputedStyle(p);
      if (/(auto|scroll)/.test(cs.overflowY) && p.scrollHeight > p.clientHeight + 2) {
        const er = el.getBoundingClientRect();
        const pr = p.getBoundingClientRect();
        const s = state.scale || 0.5;
        if (er.top < pr.top + 8) p.scrollTop -= (pr.top + 8 - er.top) / s;
        else if (er.bottom > pr.bottom - 8) p.scrollTop += (er.bottom - pr.bottom + 8) / s;
        return;
      }
      p = p.parentElement;
    }
  }

  function focusEl(el) {
    if (!el || !isVisible(el)) return;
    $$('.focused').forEach((e) => e.classList.remove('focused'));
    el.classList.add('focused');
    state.focusEl = el;
    state.focusMemory[scopeKey()] = el.dataset.focusId || el.id;
    ensureVisible(el);
  }

  function focusById(id, fallbackScope) {
    const scope = fallbackScope || focusScope();
    const el = $(`[data-focus-id="${id}"]`, scope) || $(`[data-focus-id="${id}"]`);
    if (el && isVisible(el)) { focusEl(el); return true; }
    const first = focusablesIn(scope)[0];
    if (first) { focusEl(first); return true; }
    return false;
  }

  function focusDefault(delay = true) {
    const run = () => {
      if (state.overlay) {
        const first = focusablesIn(focusScope())[0];
        if (first) focusEl(first);
        return;
      }
      if (state.screen === 'home') focusById(state.focusMemory.home || 'home-card-1');
      else if (state.screen === 'player') focusById(state.focusMemory.player || 'player-play');
    };
    if (delay) requestAnimationFrame(run); else run();
  }

  function showToast(text, ms = 2200) {
    clearTimeout(state.toastTimer);
    toastEl.textContent = text;
    toastEl.hidden = false;
    state.toastTimer = setTimeout(() => { toastEl.hidden = true; }, ms);
  }

  function silenceToast() {
    clearTimeout(state.toastTimer);
    toastEl.hidden = true;
  }

  function showHud(text, percent) {
    clearTimeout(state.hudTimer);
    hud.innerHTML = `<span>${text}</span>` + (percent !== undefined
      ? `<span class="bar-mini"><i style="width:${Math.max(0, Math.min(100, percent))}%"></i></span>` : '');
    hud.hidden = false;
    state.hudTimer = setTimeout(() => { hud.hidden = true; }, 1500);
  }

  const formatTime = (sec) => {
    const s = Math.max(0, Math.round(sec));
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  };

  function parseTime(t) {
    const [m, s] = String(t).split(':').map(Number);
    return m * 60 + s;
  }

  /* ---------------- mode / screen ---------------- */

  function setMode(mode) {
    state.mode = mode;
    app.dataset.mode = mode === 'topPlayer' ? 'top'
      : mode === 'bottomUi' ? 'bottom'
      : mode === 'overlay' ? 'overlay' : 'browse';
    renderMode();
  }

  function renderMode() {
    const info = MODE_INFO[state.mode] || MODE_INFO.browse;
    let label = info.label;
    let mid = info.mid;
    let right = info.right;

    if (state.mode === 'overlay') {
      const names = { quality: '清晰度', speed: '播放速度', more: '更多', exit: '退出确认' };
      mid = `${names[state.overlay] || '菜单'} · A 确认`;
      right = 'B 关闭';
    }

    modeDot.className = 'dot ' + info.dot;
    modeLabel.textContent = label;
    modeMid.textContent = mid;
    modeRight.textContent = right;

    guideModeDot.className = 'dot ' + info.dot;
    guideMode.textContent = label;
    guideHint.innerHTML = info.hint;
  }

  function setScreen(name) {
    state.screen = name;
    browseShell.hidden = name === 'player';
    screenPlayer.hidden = name !== 'player';
    screenHome.hidden = name !== 'home';
    const railHome = $('[data-focus-id="rail-home"]');
    railHome?.classList.toggle('active', name === 'home');
    if (name !== 'player') setMode('browse');
    focusDefault();
  }

  /* ---------------- tabs ---------------- */

  function activateTab(group, tab, focusTab = false) {
    $$(`[data-tab-group="${group}"]`).forEach((t) => {
      t.classList.toggle('active', t.dataset.tab === tab);
    });
    $$(`[data-pane-group="${group}"]`).forEach((p) => {
      p.hidden = p.dataset.pane !== tab;
    });
    if (group === 'player') {
      state.playerTab = tab;
      const sortRow = $('#playerSortRow');
      if (sortRow) sortRow.hidden = tab !== 'comments';
    }
    if (focusTab) {
      const el = $(`[data-tab-group="${group}"][data-tab="${tab}"]`);
      if (el && isVisible(el)) focusEl(el);
    }
    // 重新布局后，如果当前焦点落在被隐藏的 pane 里，移到 tab
    if (state.focusEl && !isVisible(state.focusEl)) {
      const el = $(`[data-tab-group="${group}"][data-tab="${tab}"]`);
      if (el && isVisible(el)) focusEl(el);
    }
  }

  function cycleTab(group, dir) {
    const tabs = $$(`[data-tab-group="${group}"]`).filter(isVisible);
    if (!tabs.length) return;
    let idx = tabs.findIndex((t) => t.classList.contains('active'));
    if (idx < 0) idx = 0;
    idx = (idx + dir + tabs.length) % tabs.length;
    const tab = tabs[idx];
    activateTab(group, tab.dataset.tab, false);
    focusEl(tab);
    if (group === 'home') showToast(`已切换到「${tab.textContent.trim()}」（原型）`);
  }

  /* ---------------- spatial focus ---------------- */

  function moveFocus(dir) {
    const scope = focusScope();
    const items = focusablesIn(scope);
    if (!items.length) return;
    const cur = state.focusEl && items.includes(state.focusEl) ? state.focusEl : null;
    if (!cur) { focusEl(items[0]); return; }

    if ((dir === 'left' || dir === 'right') && cur.dataset.tabGroup) {
      cycleTab(cur.dataset.tabGroup, dir === 'right' ? 1 : -1);
      return;
    }

    const override = cur.dataset['nav' + dir[0].toUpperCase() + dir.slice(1)];
    if (override) {
      const target = $(`[data-focus-id="${override}"]`, scope);
      if (target && isVisible(target)) { focusEl(target); return; }
    }

    const cr = cur.getBoundingClientRect();
    const cx = cr.left + cr.width / 2;
    const cy = cr.top + cr.height / 2;
    let best = null;
    let bestScore = Infinity;

    for (const el of items) {
      if (el === cur) continue;
      const r = el.getBoundingClientRect();
      const x = r.left + r.width / 2;
      const y = r.top + r.height / 2;
      const dx = x - cx;
      const dy = y - cy;
      let primary = 0;
      let secondary = 0;

      if (dir === 'left') { if (dx > -8) continue; primary = -dx; secondary = Math.abs(dy); }
      else if (dir === 'right') { if (dx < 8) continue; primary = dx; secondary = Math.abs(dy); }
      else if (dir === 'up') { if (dy > -8) continue; primary = -dy; secondary = Math.abs(dx); }
      else { if (dy < 8) continue; primary = dy; secondary = Math.abs(dx); }

      // 垂直/水平重叠越多，越优先；否则加惩罚，避免斜对角乱跳
      const overlap = (dir === 'left' || dir === 'right')
        ? Math.max(0, Math.min(cr.bottom, r.bottom) - Math.max(cr.top, r.top))
        : Math.max(0, Math.min(cr.right, r.right) - Math.max(cr.left, r.left));
      const penalty = overlap > 0 ? 0 : 46;
      const score = primary + secondary * 1.7 + penalty;
      if (score < bestScore) { bestScore = score; best = el; }
    }

    if (best) focusEl(best);
  }

  /* ---------------- overlays ---------------- */

  function openOverlay(name, fromEl) {
    state.overlayReturnId = (fromEl && fromEl.dataset.focusId) || (state.focusEl && state.focusEl.dataset.focusId) || null;
    state.overlay = name;
    overlayHost.hidden = false;
    $$('.sheet, .dialog', overlayHost).forEach((el) => {
      el.hidden = el.dataset.overlay !== name;
    });
    setMode('overlay');
    if (name === 'quality') $('#qualitySheetCurrent').textContent = state.quality;
    if (name === 'speed') $('#speedSheetCurrent').textContent = formatSpeed(state.speed);
    if (name === 'exit') setPlaying(false);
    requestAnimationFrame(() => {
      const first = focusablesIn(focusScope())[0];
      if (first) focusEl(first);
    });
  }

  function closeOverlay() {
    if (!state.overlay) return;
    const returnId = state.overlayReturnId;
    state.overlay = null;
    overlayHost.hidden = true;
    setMode('bottomUi');
    if (returnId) focusById(returnId);
  }

  /* ---------------- playback ---------------- */

  function updateProgress() {
    const pct = state.duration > 0 ? Math.max(0, Math.min(100, state.position / state.duration * 100)) : 0;
    $('#playerTime').textContent = formatTime(state.position);
    const dur = $('#playerDuration') || $('#screenPlayer .times span:last-child');
    if (dur) dur.textContent = formatTime(state.duration);
    $('#playerFill').style.width = pct + '%';
    $('#playerKnob').style.left = pct + '%';
  }

  function setPlaying(v) {
    state.playing = v;
    $('#playerPlay').classList.toggle('playing', v);
    topVideo.classList.toggle('paused', !v);
    renderMode();
  }

  function startPlayback(title) {
    state.videoStarted = true;
    state.part = 1;
    state.position = title ? 0 : 201;
    state.duration = 754;
    state.playing = true;
    state.playerTab = 'comments';
    $('#playerTitle').textContent = title || '双屏掌机上的 B 站客户端，到底该怎么设计？';
    $('#playerSub').textContent = '数码研究所 · 12.3万播放 · P1 / 12P';
    $('#queueTitle').textContent = 'P2 安装与初始配置';
    $('#qualityChip').textContent = state.quality;
    $('#playerQualityBadge').textContent = state.quality;
    $('#speedChip').textContent = formatSpeed(state.speed);
    $('#volumeChip').textContent = `音量 ${state.volume}%`;
    $('#danmakuChip').textContent = state.danmaku ? '开' : '关';
    $('#player-danmaku').classList.toggle('on', state.danmaku);
    danmakuLayer.classList.toggle('off', !state.danmaku);
    topIdle.hidden = true;
    topVideo.classList.add('on');
    topVideo.classList.remove('paused');
    $('#playerPlay').classList.add('playing');
    setScreen('player');
    activateTab('player', 'comments');
    setMode('topPlayer');
    updateProgress();
    focusById('player-play');
    highlightPart(1);
    showToast('上屏开始播放 · 下屏控制台已就绪');
  }

  function exitPlayback() {
    state.videoStarted = false;
    state.playing = false;
    state.overlay = null;
    overlayHost.hidden = true;
    topVideo.classList.remove('on', 'paused');
    topIdle.hidden = false;
    $('#playerPlay').classList.remove('playing');
    setScreen('home');
    setMode('browse');
    focusById(state.focusMemory.home || 'home-card-1');
    showToast('已退出播放，回到首页');
  }

  function togglePlay() {
    if (!state.videoStarted) { startPlayback(); return; }
    setPlaying(!state.playing);
    showToast(state.playing ? '继续播放' : '已暂停');
  }

  function toggleDanmaku() {
    if (!state.videoStarted) { showToast('当前没有播放'); return; }
    state.danmaku = !state.danmaku;
    danmakuLayer.classList.toggle('off', !state.danmaku);
    $('#player-danmaku').classList.toggle('on', state.danmaku);
    $('#danmakuChip').textContent = state.danmaku ? '开' : '关';
    showToast(`弹幕：${state.danmaku ? '开' : '关'}`);
  }

  function seek(delta) {
    if (!state.videoStarted) return;
    state.position = Math.max(0, Math.min(state.duration, state.position + delta));
    updateProgress();
    showHud(delta > 0 ? '+10 秒' : '−10 秒');
  }

  function changeVolume(delta) {
    if (!state.videoStarted) return;
    state.volume = Math.max(0, Math.min(100, state.volume + delta));
    $('#volumeChip').textContent = `音量 ${state.volume}%`;
    showHud(`音量 ${state.volume}%`, state.volume);
  }

  function formatSpeed(v) {
    const n = Number(v);
    return (Number.isInteger(n) ? n.toFixed(1) : String(n)) + '×';
  }

  function setSpeed(v) {
    state.speed = Number(v);
    $('#speedChip').textContent = formatSpeed(state.speed);
    $('#speedSheetCurrent').textContent = formatSpeed(state.speed);
    $$('[data-action="set-speed"]').forEach((el) => {
      el.classList.toggle('on', Number(el.dataset.value) === state.speed);
    });
    closeOverlay();
    showToast(`倍速：${formatSpeed(state.speed)}`);
  }

  function setQuality(v) {
    state.quality = v;
    $('#qualityChip').textContent = v;
    $('#playerQualityBadge').textContent = v;
    $('#qualitySheetCurrent').textContent = v;
    $$('[data-action="set-quality"]').forEach((el) => {
      el.classList.toggle('selected', el.dataset.value === v);
    });
    closeOverlay();
    showToast(`清晰度：${v}`);
  }

  function highlightPart(p) {
    $$('[data-focus-id^="player-part-"]').forEach((el) => {
      el.classList.toggle('current', Number(el.dataset.part) === p);
    });
  }

  function setPart(p, title, duration) {
    state.part = p;
    state.position = 0;
    const info = PARTS[p] || { title: `P${p}`, duration: '10:00' };
    state.duration = parseTime(duration || info.duration);
    $('#playerSub').textContent = `数码研究所 · 12.3万播放 · P${p} / 12P`;
    if (title) $('#playerTitle').textContent = title;
    updateProgress();
    highlightPart(p);
    const q = $('#queueTitle');
    if (q) q.textContent = (PARTS[p + 1] || { title: `P${p + 1}` }).title;
    showToast(`切换到 P${p}：${title || info.title}`);
  }

  function partDelta(d) {
    const p = Math.max(1, Math.min(12, state.part + d));
    const info = PARTS[p] || { title: `P${p}`, duration: '10:00' };
    setPart(p, info.title, info.duration);
  }

  /* ---------------- actions ---------------- */

  function doAction(action, el) {
    if (!action) return;
    switch (action) {
      case 'rail-home': setScreen('home'); break;
      case 'rail': showToast(`原型未实现：${el.dataset.label || '该页面'}`); break;
      case 'refresh': showToast('已刷新（原型）'); break;
      case 'tab-home':
      case 'tab-player': {
        const group = el.dataset.tabGroup;
        activateTab(group, el.dataset.tab, false);
        focusEl(el);
        break;
      }
      case 'play-card': startPlayback(el.dataset.title); break;
      case 'back': onBack(); break;
      case 'play': startPlayback(); break;
      case 'toggle-play': togglePlay(); break;
      case 'quality': openOverlay('quality', el); break;
      case 'speed': openOverlay('speed', el); break;
      case 'more': openOverlay('more', el); break;
      case 'exit': openOverlay('exit', el); break;
      case 'danmaku': toggleDanmaku(); break;
      case 'volume': changeVolume(10); break;
      case 'seek-back': seek(-10); break;
      case 'seek-fwd': seek(10); break;
      case 'part-prev': partDelta(-1); break;
      case 'part-next': partDelta(1); break;
      case 'part': setPart(Number(el.dataset.part), el.dataset.title, el.dataset.duration); break;
      case 'related': startPlayback(el.dataset.title); break;
      case 'comment': showToast('已点赞（原型）'); break;
      case 'follow':
        state.following = !state.following;
        el.textContent = state.following ? '已关注' : '关注';
        el.classList.toggle('on', state.following);
        showToast(state.following ? '已关注 UP 主' : '已取消关注');
        break;
      case 'fav':
        state.faved = !state.faved;
        el.textContent = state.faved ? '已收藏' : '收藏';
        el.classList.toggle('on', state.faved);
        showToast(state.faved ? '已收藏' : '已取消收藏');
        break;
      case 'download': showToast('已加入下载队列（原型）'); break;
      case 'sort':
        $$('.sort span').forEach((s) => s.classList.toggle('on', s === el));
        showToast(`排序：${el.textContent.trim()}（原型）`);
        break;
      case 'cancel': closeOverlay(); break;
      case 'confirm-exit': exitPlayback(); break;
      case 'set-quality': setQuality(el.dataset.value); break;
      case 'set-speed': setSpeed(el.dataset.value); break;
      case 'toast': showToast(el.dataset.msg || '原型操作'); break;
      default: showToast('原型操作');
    }
  }

  function onConfirm() {
    if (state.overlay) {
      const el = state.focusEl;
      if (el && el.dataset.action) doAction(el.dataset.action, el);
      return;
    }
    if (state.screen === 'player' && state.mode === 'topPlayer') {
      togglePlay();
      return;
    }
    const el = state.focusEl;
    if (el && el.dataset.action) doAction(el.dataset.action, el);
  }

  function onBack() {
    if (state.overlay) { closeOverlay(); return; }
    if (state.screen === 'player') {
      if (state.mode === 'bottomUi') { setMode('topPlayer'); showToast('手柄交回播放'); }
      else openOverlay('exit');
      return;
    }
    if (state.screen === 'home') {
      showToast('已在首页');
      return;
    }
  }

  function onX() {
    if (state.screen === 'player' && state.mode === 'topPlayer') { toggleDanmaku(); return; }
    if (state.screen === 'player' && state.mode === 'bottomUi') { showToast('上下文操作（原型）'); return; }
    showToast('上下文操作（原型）');
    return;
  }

  function onY() {
    if (state.overlay) { showToast('先关闭菜单'); return; }
    if (!state.videoStarted) { showToast('当前没有播放'); return; }
    if (state.mode === 'topPlayer') {
      setMode('bottomUi');
      focusById(state.focusMemory.player || 'player-play');
      showToast('手柄现在控制下屏');
    } else {
      setMode('topPlayer');
      showToast('手柄现在控制播放');
    }
  }

  function onStart() {
    if (state.overlay) { showToast('先关闭菜单'); return; }
    if (state.screen === 'player' && state.mode === 'topPlayer') { openOverlay('quality'); return; }
    if (state.screen === 'player') { openOverlay('more'); return; }
    showToast('菜单（原型）');
  }

  function onBumper(d) {
    if (state.screen === 'player') { partDelta(d); return; }
    showToast('原型：LB/RB 切页');
  }

  function onTrigger(d) {
    if (state.screen === 'player' && state.mode === 'topPlayer') {
      const next = Math.max(0.5, Math.min(2, Math.round((state.speed + d * 0.25) * 100) / 100));
      state.speed = next;
      $('#speedChip').textContent = formatSpeed(next);
      $$('[data-action="set-speed"]').forEach((el) => {
        el.classList.toggle('on', Number(el.dataset.value) === next);
      });
      showHud(`倍速 ${formatSpeed(next)}`);
      return;
    }
    if (state.screen === 'player') { cycleTab('player', d); return; }
    cycleTab('home', d);
  }

  /* ---------------- keyboard ---------------- */

  function onArrow(dir) {
    if (state.overlay) { moveFocus(dir); return; }
    if (state.screen === 'player' && state.mode === 'topPlayer') {
      if (dir === 'left') seek(-10);
      else if (dir === 'right') seek(10);
      else if (dir === 'up') changeVolume(5);
      else changeVolume(-5);
      return;
    }
    moveFocus(dir);
  }

  window.addEventListener('keydown', (e) => {
    const k = e.key;
    const prevent = ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Enter', ' ', 'Escape', 'Backspace', 'Tab'];
    if (prevent.includes(k)) e.preventDefault();

    if (k === 'Tab' && e.shiftKey) { onBack(); return; }
    if (k === 'Tab') { onStart(); return; }

    if (e.repeat && ['Enter', ' ', 'Escape', 'Backspace', 'x', 'X', 'y', 'Y'].includes(k)) return;

    switch (k) {
      case 'ArrowUp': onArrow('up'); break;
      case 'ArrowDown': onArrow('down'); break;
      case 'ArrowLeft': onArrow('left'); break;
      case 'ArrowRight': onArrow('right'); break;
      case 'Enter':
      case ' ': onConfirm(); break;
      case 'Escape':
      case 'Backspace': onBack(); break;
      case 'x': case 'X': onX(); break;
      case 'y': case 'Y': onY(); break;
      case 'q': case 'Q': onBumper(-1); break;
      case 'e': case 'E': onBumper(1); break;
      case '1': onTrigger(-1); break;
      case '2': onTrigger(1); break;
      default: break;
    }
  });

  /* ---------------- pointer ---------------- */

  bottomScreen.addEventListener('pointerdown', (e) => {
    if (state.screen === 'player' && state.mode === 'topPlayer' && !state.overlay) {
      setMode('bottomUi');
      const el = e.target.closest('[data-focusable]');
      if (el && isVisible(el)) focusEl(el);
      state.swallowClick = true;
      clearTimeout(state.swallowTimer);
      state.swallowTimer = setTimeout(() => { state.swallowClick = false; }, 600);
    }
  }, true);

  app.addEventListener('click', (e) => {
    if (state.swallowClick) { state.swallowClick = false; return; }
    const el = e.target.closest('[data-focusable]');
    if (!el || !isVisible(el)) return;
    if (state.screen === 'player' && state.mode === 'topPlayer' && !state.overlay) {
      setMode('bottomUi');
      focusEl(el);
      return;
    }
    focusEl(el);
    if (el.dataset.action) doAction(el.dataset.action, el);
  });

  /* ---------------- top video ---------------- */

  function buildDanmaku() {
    const texts = [
      '这波操作可以', '下屏控制台太香了', '上屏终于干净了', '4:3 刚刚好',
      '手柄切换很直观', '双屏正确用法', '这焦点设计舒服', 'B 站客户端就该这样',
      '求量产', '上屏只放视频 +1', '不要 OSD！', '评论放下面太合理了',
      'Y 键切模式好评', '触摸和手柄终于不打架', '这个模式条很关键', '希望早点用上',
    ];
    danmakuLayer.innerHTML = '';
    texts.forEach((t, i) => {
      const span = document.createElement('span');
      span.className = 'dm' + (i % 7 === 0 ? ' big' : '');
      span.textContent = t;
      span.style.top = (6 + Math.random() * 72) + '%';
      span.style.animationDuration = (7 + Math.random() * 5).toFixed(1) + 's';
      span.style.animationDelay = (-Math.random() * 12).toFixed(1) + 's';
      danmakuLayer.appendChild(span);
    });
  }

  /* ---------------- init / reset ---------------- */

  function reset() {
    state.screen = 'home';
    state.mode = 'browse';
    state.overlay = null;
    state.overlayReturnId = null;
    state.videoStarted = false;
    state.playing = true;
    state.position = 201;
    state.duration = 754;
    state.part = 1;
    state.quality = '1080P';
    state.speed = 1;
    state.volume = 80;
    state.danmaku = true;
    state.following = false;
    state.faved = false;
    state.focusEl = null;
    state.focusMemory = {};
    overlayHost.hidden = true;
    $$('.sheet, .dialog', overlayHost).forEach((el) => { el.hidden = true; });
    topVideo.classList.remove('on', 'paused');
    topIdle.hidden = false;
    $('#playerPlay').classList.remove('playing');
    $('#playerTitle').textContent = '双屏掌机上的 B 站客户端，到底该怎么设计？';
    $('#playerSub').textContent = '数码研究所 · 12.3万播放 · P1 / 12P';
    $('#qualityChip').textContent = '1080P';
    $('#playerQualityBadge').textContent = '1080P';
    $('#speedChip').textContent = '1.0×';
    $('#volumeChip').textContent = '音量 80%';
    $('#danmakuChip').textContent = '开';
    $('#player-danmaku').classList.add('on');
    danmakuLayer.classList.remove('off');
    $('#queueTitle').textContent = 'P2 安装与初始配置';
    const follow = $('#player-follow');
    follow.textContent = '关注';
    follow.classList.remove('on');
    const fav = $('#player-fav');
    fav.textContent = '收藏';
    fav.classList.remove('on');
    $$('[data-action="set-quality"]').forEach((el) => el.classList.toggle('selected', el.dataset.value === '1080P'));
    $$('[data-action="set-speed"]').forEach((el) => el.classList.toggle('on', Number(el.dataset.value) === 1));
    highlightPart(1);
    updateProgress();
    activateTab('player', 'comments');
    setScreen('home');
    setMode('browse');
    focusById('home-card-1');
    showToast('原型已重置');
  }

  function applyUrlState() {
    const params = new URLSearchParams(location.search);
    const s = params.get('state');
    const scale = Number(params.get('scale'));
    if (scale && [0.45, 0.5, 0.6, 0.7].includes(scale)) setScale(scale);

    if (s === 'player-top') { startPlayback(); silenceToast(); return; }
    if (s === 'player-bottom') { startPlayback(); setMode('bottomUi'); focusById('player-play'); silenceToast(); return; }
    if (s === 'intro') {
      startPlayback();
      setMode('bottomUi');
      activateTab('player', 'intro', true);
      focusById('player-follow');
      silenceToast();
      return;
    }
    if (s && ['quality', 'speed', 'more', 'exit'].includes(s)) {
      startPlayback();
      setMode('bottomUi');
      openOverlay(s, $(`[data-focus-id="player-${s === 'quality' ? 'quality' : s === 'speed' ? 'speed' : 'more'}"]`));
      silenceToast();
      return;
    }
  }

  function setScale(s) {
    state.scale = s;
    document.documentElement.style.setProperty('--s', String(s));
    $('#zoomBtn').textContent = `缩放 ${Math.round(s * 100)}%`;
  }

  function init() {
    buildDanmaku();
    setScale(0.5);
    $('#zoomBtn').addEventListener('click', () => {
      const order = [0.5, 0.6, 0.7, 0.45];
      const idx = order.indexOf(state.scale);
      setScale(order[(idx + 1) % order.length]);
    });
    $('#resetBtn').addEventListener('click', reset);

    updateProgress();
    activateTab('player', 'comments');
    setScreen('home');
    setMode('browse');
    focusDefault(false);
    try { applyUrlState(); } catch (err) { console.error(err); }

    state.tickTimer = setInterval(() => {
      if (state.videoStarted && state.playing && !state.overlay) {
        state.position = Math.min(state.duration, state.position + 1);
        updateProgress();
        if (state.position >= state.duration) {
          setPlaying(false);
          showToast('播放结束');
        }
      }
    }, 1000);
  }

  init();
})();
