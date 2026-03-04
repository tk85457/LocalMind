"use strict";

(function bootstrap() {
  const canvas = document.getElementById("game-canvas");
  const ctx = canvas.getContext("2d");
  const WIDTH = canvas.width;
  const HEIGHT = canvas.height;
  const FIXED_DT = 1 / 60;

  const input = {
    left: false,
    right: false,
    up: false,
    down: false,
    fireQueued: false,
    startQueued: false,
    pauseQueued: false,
  };

  const state = {
    mode: "start",
    score: 0,
    health: 4,
    timer: 75,
    requiredOrbs: 6,
    player: null,
    orbs: [],
    drones: [],
    bolts: [],
    elapsed: 0,
  };

  const orbLayout = [
    { x: 480, y: 190 },
    { x: 575, y: 235 },
    { x: 560, y: 335 },
    { x: 465, y: 380 },
    { x: 360, y: 320 },
    { x: 365, y: 215 },
  ];

  const droneLayout = [
    { x: 810, y: 140 },
    { x: 160, y: 430 },
    { x: 820, y: 360 },
  ];

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function length2d(x, y) {
    return Math.hypot(x, y);
  }

  function roundTo(value, digits) {
    const factor = 10 ** digits;
    return Math.round(value * factor) / factor;
  }

  function mapPointToCanvas(event) {
    const rect = canvas.getBoundingClientRect();
    const x = (event.clientX - rect.left) * (canvas.width / rect.width);
    const y = (event.clientY - rect.top) * (canvas.height / rect.height);
    return { x, y };
  }

  function initRound() {
    state.mode = "playing";
    state.score = 0;
    state.health = 4;
    state.timer = 75;
    state.elapsed = 0;
    state.bolts = [];
    state.player = {
      x: WIDTH / 2,
      y: HEIGHT / 2,
      vx: 0,
      vy: 0,
      r: 14,
      speed: 250,
      facingX: 1,
      facingY: 0,
      shotCooldown: 0,
      invuln: 0,
    };
    state.orbs = orbLayout.map((slot) => ({
      x: slot.x,
      y: slot.y,
      r: 11,
      collected: false,
    }));
    state.drones = droneLayout.map((slot, idx) => ({
      id: idx + 1,
      homeX: slot.x,
      homeY: slot.y,
      x: slot.x,
      y: slot.y,
      r: 16,
      speed: 72,
      stun: 0,
    }));
  }

  function handleMetaInputs() {
    if (input.pauseQueued) {
      if (state.mode === "playing") {
        state.mode = "paused";
      } else if (state.mode === "paused") {
        state.mode = "playing";
      }
      input.pauseQueued = false;
    }

    if (input.startQueued) {
      if (state.mode === "start" || state.mode === "won" || state.mode === "lost") {
        initRound();
      } else if (state.mode === "paused") {
        state.mode = "playing";
      }
      input.startQueued = false;
    }
  }

  function spawnBolt(directionX, directionY) {
    const dx = directionX || state.player.facingX || 1;
    const dy = directionY || state.player.facingY || 0;
    const len = length2d(dx, dy) || 1;
    const nx = dx / len;
    const ny = dy / len;
    state.player.facingX = nx;
    state.player.facingY = ny;
    state.player.shotCooldown = 0.3;
    state.bolts.push({
      x: state.player.x + nx * (state.player.r + 7),
      y: state.player.y + ny * (state.player.r + 7),
      vx: nx * 430,
      vy: ny * 430,
      r: 4,
      ttl: 0.95,
    });
  }

  function recoverDrone(drone) {
    drone.x = drone.homeX;
    drone.y = drone.homeY;
    drone.stun = 0.9;
  }

  function updatePlayer(dt) {
    const moveX = (input.right ? 1 : 0) - (input.left ? 1 : 0);
    const moveY = (input.down ? 1 : 0) - (input.up ? 1 : 0);
    const axisLength = length2d(moveX, moveY) || 1;
    const normX = moveX / axisLength;
    const normY = moveY / axisLength;

    state.player.vx = normX * state.player.speed;
    state.player.vy = normY * state.player.speed;
    state.player.x += state.player.vx * dt;
    state.player.y += state.player.vy * dt;
    state.player.x = clamp(state.player.x, state.player.r, WIDTH - state.player.r);
    state.player.y = clamp(state.player.y, state.player.r, HEIGHT - state.player.r);

    if (moveX !== 0 || moveY !== 0) {
      state.player.facingX = normX;
      state.player.facingY = normY;
    }

    state.player.shotCooldown = Math.max(0, state.player.shotCooldown - dt);
    state.player.invuln = Math.max(0, state.player.invuln - dt);

    if (input.fireQueued && state.player.shotCooldown <= 0) {
      spawnBolt(state.player.facingX, state.player.facingY);
    }
    input.fireQueued = false;
  }

  function updateBolts(dt) {
    const survivors = [];
    for (const bolt of state.bolts) {
      bolt.x += bolt.vx * dt;
      bolt.y += bolt.vy * dt;
      bolt.ttl -= dt;
      if (
        bolt.ttl > 0 &&
        bolt.x >= -bolt.r &&
        bolt.y >= -bolt.r &&
        bolt.x <= WIDTH + bolt.r &&
        bolt.y <= HEIGHT + bolt.r
      ) {
        survivors.push(bolt);
      }
    }
    state.bolts = survivors;
  }

  function updateDrones(dt) {
    for (const drone of state.drones) {
      if (drone.stun > 0) {
        drone.stun = Math.max(0, drone.stun - dt);
        continue;
      }
      const dx = state.player.x - drone.x;
      const dy = state.player.y - drone.y;
      const dist = length2d(dx, dy) || 1;
      const speedBoost = clamp(1 + (75 - state.timer) * 0.015, 1, 2.05);
      const step = drone.speed * speedBoost * dt;
      drone.x += (dx / dist) * step;
      drone.y += (dy / dist) * step;
    }
  }

  function resolveOrbCollects() {
    for (const orb of state.orbs) {
      if (orb.collected) continue;
      const dx = state.player.x - orb.x;
      const dy = state.player.y - orb.y;
      const hitRadius = state.player.r + orb.r;
      if (dx * dx + dy * dy <= hitRadius * hitRadius) {
        orb.collected = true;
        state.score += 10;
      }
    }
  }

  function resolveBoltHits() {
    const aliveBolts = [];
    for (const bolt of state.bolts) {
      let consumed = false;
      for (const drone of state.drones) {
        const dx = bolt.x - drone.x;
        const dy = bolt.y - drone.y;
        const hitRadius = bolt.r + drone.r;
        if (dx * dx + dy * dy <= hitRadius * hitRadius) {
          consumed = true;
          state.score += 2;
          recoverDrone(drone);
          break;
        }
      }
      if (!consumed) aliveBolts.push(bolt);
    }
    state.bolts = aliveBolts;
  }

  function resolveDroneHits() {
    if (state.player.invuln > 0) return;
    for (const drone of state.drones) {
      const dx = state.player.x - drone.x;
      const dy = state.player.y - drone.y;
      const hitRadius = state.player.r + drone.r;
      if (dx * dx + dy * dy <= hitRadius * hitRadius) {
        state.health = Math.max(0, state.health - 1);
        state.player.invuln = 1.25;
        const dist = length2d(dx, dy) || 1;
        state.player.x = clamp(state.player.x + (dx / dist) * 26, state.player.r, WIDTH - state.player.r);
        state.player.y = clamp(state.player.y + (dy / dist) * 26, state.player.r, HEIGHT - state.player.r);
        break;
      }
    }
  }

  function evaluateWinLoss() {
    const remaining = state.orbs.filter((orb) => !orb.collected).length;
    if (remaining <= state.orbs.length - state.requiredOrbs) {
      state.mode = "won";
      return;
    }
    if (state.health <= 0 || state.timer <= 0) {
      state.mode = "lost";
    }
  }

  function update(dt) {
    handleMetaInputs();
    if (state.mode !== "playing") return;

    state.elapsed += dt;
    state.timer = Math.max(0, state.timer - dt);

    updatePlayer(dt);
    updateBolts(dt);
    updateDrones(dt);
    resolveOrbCollects();
    resolveBoltHits();
    resolveDroneHits();
    evaluateWinLoss();
  }

  function drawBackground() {
    const sky = ctx.createLinearGradient(0, 0, 0, HEIGHT);
    sky.addColorStop(0, "#0d1f2d");
    sky.addColorStop(1, "#163955");
    ctx.fillStyle = sky;
    ctx.fillRect(0, 0, WIDTH, HEIGHT);

    ctx.globalAlpha = 0.2;
    ctx.strokeStyle = "#9bd4ff";
    for (let x = 0; x < WIDTH; x += 48) {
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, HEIGHT);
      ctx.stroke();
    }
    for (let y = 0; y < HEIGHT; y += 48) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(WIDTH, y);
      ctx.stroke();
    }
    ctx.globalAlpha = 1;
  }

  function drawOrbs() {
    for (const orb of state.orbs) {
      if (orb.collected) continue;
      ctx.beginPath();
      ctx.fillStyle = "#ffd447";
      ctx.shadowColor = "#ffe07e";
      ctx.shadowBlur = 16;
      ctx.arc(orb.x, orb.y, orb.r, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;
      ctx.beginPath();
      ctx.fillStyle = "#fff4be";
      ctx.arc(orb.x - 3, orb.y - 3, 3, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  function drawDrones() {
    for (const drone of state.drones) {
      const pulse = 0.75 + Math.sin((state.elapsed + drone.id) * 5) * 0.1;
      ctx.save();
      ctx.translate(drone.x, drone.y);
      ctx.rotate(state.elapsed * 0.75 + drone.id);
      ctx.globalAlpha = drone.stun > 0 ? 0.4 : 1;
      ctx.fillStyle = drone.stun > 0 ? "#89e0ff" : "#ff6d6d";
      ctx.beginPath();
      ctx.moveTo(0, -drone.r * pulse);
      ctx.lineTo(drone.r * 0.75, 0);
      ctx.lineTo(0, drone.r * pulse);
      ctx.lineTo(-drone.r * 0.75, 0);
      ctx.closePath();
      ctx.fill();
      ctx.restore();
    }
  }

  function drawBolts() {
    for (const bolt of state.bolts) {
      ctx.beginPath();
      ctx.fillStyle = "#7ef8ff";
      ctx.arc(bolt.x, bolt.y, bolt.r, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  function drawPlayer() {
    const flicker = state.player.invuln > 0 && Math.floor(state.elapsed * 12) % 2 === 0;
    if (flicker) return;

    ctx.save();
    ctx.translate(state.player.x, state.player.y);
    ctx.fillStyle = "#67b0ff";
    ctx.beginPath();
    ctx.arc(0, 0, state.player.r, 0, Math.PI * 2);
    ctx.fill();

    ctx.strokeStyle = "#d8edff";
    ctx.lineWidth = 3;
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.lineTo(state.player.facingX * 19, state.player.facingY * 19);
    ctx.stroke();

    ctx.fillStyle = "#0b2940";
    ctx.beginPath();
    ctx.arc(-4, -2, 2.5, 0, Math.PI * 2);
    ctx.arc(4, -2, 2.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  function drawHud() {
    const remaining = state.orbs.filter((orb) => !orb.collected).length;
    ctx.fillStyle = "rgba(8, 18, 28, 0.65)";
    ctx.fillRect(14, 14, 340, 64);
    ctx.fillStyle = "#dff3ff";
    ctx.font = "20px Trebuchet MS";
    ctx.fillText(`Score ${state.score}`, 28, 40);
    ctx.fillText(`Health ${state.health}`, 142, 40);
    ctx.fillText(`Time ${Math.ceil(state.timer)}`, 262, 40);
    ctx.font = "14px Trebuchet MS";
    ctx.fillText(`Orbs left ${remaining}`, 28, 62);
  }

  function drawOverlay(title, subtitle, tertiary) {
    ctx.fillStyle = "rgba(4, 11, 18, 0.72)";
    ctx.fillRect(0, 0, WIDTH, HEIGHT);
    ctx.fillStyle = "#f3fbff";
    ctx.textAlign = "center";
    ctx.font = "bold 56px Trebuchet MS";
    ctx.fillText(title, WIDTH / 2, HEIGHT / 2 - 84);
    ctx.font = "24px Trebuchet MS";
    ctx.fillText(subtitle, WIDTH / 2, HEIGHT / 2 - 34);
    ctx.font = "20px Trebuchet MS";
    ctx.fillText(tertiary, WIDTH / 2, HEIGHT / 2 + 8);
    ctx.font = "18px Trebuchet MS";
    ctx.fillStyle = "#bee6ff";
    ctx.fillText("Arrows move  •  Space pulse  •  A pause", WIDTH / 2, HEIGHT / 2 + 56);
    ctx.fillText("Enter or click canvas to launch/restart", WIDTH / 2, HEIGHT / 2 + 88);
    ctx.textAlign = "start";
  }

  function render() {
    drawBackground();
    if (state.mode === "start") {
      drawOverlay("Orbit Salvage", "Collect energy orbs before time runs out.", "Tag 6 orbs to win.");
      return;
    }

    drawOrbs();
    drawDrones();
    drawBolts();
    drawPlayer();
    drawHud();

    if (state.mode === "paused") {
      drawOverlay("Paused", "Simulation on hold.", "Press A or Enter to resume.");
    } else if (state.mode === "won") {
      drawOverlay("Recovered", `Final score ${state.score}.`, "Press Enter to play again.");
    } else if (state.mode === "lost") {
      drawOverlay("Signal Lost", "Hull integrity collapsed or timer expired.", "Press Enter to retry.");
    }
  }

  function renderGameToText() {
    const remainingOrbs = state.orbs.filter((orb) => !orb.collected);
    const payload = {
      coordinate_system: "origin=(0,0) at top-left; +x right; +y down; units are canvas pixels",
      fullscreen: Boolean(document.fullscreenElement),
      mode: state.mode,
      score: state.score,
      health: state.health,
      timer_seconds: roundTo(state.timer, 2),
      orbs_remaining: remainingOrbs.length,
      player: state.player
        ? {
            x: roundTo(state.player.x, 1),
            y: roundTo(state.player.y, 1),
            vx: roundTo(state.player.vx, 1),
            vy: roundTo(state.player.vy, 1),
            radius: state.player.r,
            facing: {
              x: roundTo(state.player.facingX, 2),
              y: roundTo(state.player.facingY, 2),
            },
            shot_cooldown_seconds: roundTo(state.player.shotCooldown, 2),
            invulnerable_seconds: roundTo(state.player.invuln, 2),
          }
        : null,
      orbs: remainingOrbs.map((orb) => ({
        x: roundTo(orb.x, 1),
        y: roundTo(orb.y, 1),
        radius: orb.r,
      })),
      drones: state.drones.map((drone) => ({
        x: roundTo(drone.x, 1),
        y: roundTo(drone.y, 1),
        radius: drone.r,
        stunned_seconds: roundTo(drone.stun, 2),
      })),
      bolts: state.bolts.map((bolt) => ({
        x: roundTo(bolt.x, 1),
        y: roundTo(bolt.y, 1),
        vx: roundTo(bolt.vx, 1),
        vy: roundTo(bolt.vy, 1),
        ttl_seconds: roundTo(bolt.ttl, 2),
      })),
    };
    return JSON.stringify(payload);
  }

  let manualStepping = false;
  let previousTs = performance.now();
  function frame(ts) {
    const delta = clamp((ts - previousTs) / 1000, 0, 0.05);
    previousTs = ts;
    if (!manualStepping) {
      update(delta);
    }
    render();
    window.requestAnimationFrame(frame);
  }

  function advanceDeterministic(ms) {
    manualStepping = true;
    const steps = Math.max(1, Math.round(ms / (1000 / 60)));
    for (let i = 0; i < steps; i++) {
      update(FIXED_DT);
    }
    render();
  }

  function toggleFullscreen() {
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => {});
    } else {
      canvas.requestFullscreen?.().catch(() => {});
    }
  }

  function syncCanvasPresentation() {
    if (document.fullscreenElement === canvas) {
      const viewportWidth = window.innerWidth;
      const viewportHeight = window.innerHeight;
      const scale = Math.max(0.1, Math.min(viewportWidth / WIDTH, viewportHeight / HEIGHT));
      canvas.style.width = `${Math.floor(WIDTH * scale)}px`;
      canvas.style.height = `${Math.floor(HEIGHT * scale)}px`;
      canvas.style.borderRadius = "0";
      canvas.style.borderWidth = "0";
      return;
    }
    canvas.style.width = "";
    canvas.style.height = "";
    canvas.style.borderRadius = "";
    canvas.style.borderWidth = "";
  }

  window.render_game_to_text = renderGameToText;
  window.advanceTime = advanceDeterministic;

  window.addEventListener("keydown", (event) => {
    switch (event.code) {
      case "ArrowLeft":
        input.left = true;
        break;
      case "ArrowRight":
        input.right = true;
        break;
      case "ArrowUp":
        input.up = true;
        break;
      case "ArrowDown":
        input.down = true;
        break;
      case "Space":
        input.fireQueued = true;
        event.preventDefault();
        break;
      case "Enter":
        input.startQueued = true;
        break;
      case "KeyA":
      case "KeyP":
        if (!event.repeat) input.pauseQueued = true;
        break;
      case "KeyF":
      case "KeyB":
        if (!event.repeat) toggleFullscreen();
        break;
      case "Escape":
        if (document.fullscreenElement) {
          document.exitFullscreen().catch(() => {});
        }
        break;
      default:
        break;
    }
  });

  window.addEventListener("keyup", (event) => {
    switch (event.code) {
      case "ArrowLeft":
        input.left = false;
        break;
      case "ArrowRight":
        input.right = false;
        break;
      case "ArrowUp":
        input.up = false;
        break;
      case "ArrowDown":
        input.down = false;
        break;
      default:
        break;
    }
  });

  canvas.addEventListener("pointerdown", (event) => {
    if (state.mode === "start" || state.mode === "won" || state.mode === "lost") {
      input.startQueued = true;
      return;
    }
    if (state.mode !== "playing") return;
    if (event.button !== 0) return;
    const p = mapPointToCanvas(event);
    const dx = p.x - state.player.x;
    const dy = p.y - state.player.y;
    if (state.player.shotCooldown <= 0) {
      spawnBolt(dx, dy);
    }
  });

  document.addEventListener("visibilitychange", () => {
    if (document.hidden && state.mode === "playing") {
      state.mode = "paused";
    }
  });
  document.addEventListener("fullscreenchange", syncCanvasPresentation);
  window.addEventListener("resize", syncCanvasPresentation);

  render();
  syncCanvasPresentation();
  window.requestAnimationFrame(frame);
})();
