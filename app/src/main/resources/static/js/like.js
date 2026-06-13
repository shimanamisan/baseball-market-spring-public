/**
 * お気に入りトグル（Ajax）。
 *
 * 旧 baseball-market の assets/js/main.js のいいね挙動を踏襲しつつ、jQuery を脱却し素の fetch で実装する。
 * - .js-click-like ボタンをクリックすると POST /likes/toggle に productId を送る
 * - response が no_login の場合はログインページへ誘導
 * - add / remove に応じて active クラスとハートアイコンを切り替える
 * - CSRF トークンは meta タグから読み出してヘッダに付与する
 */
(function () {
  "use strict";

  function csrf() {
    const tokenMeta = document.querySelector('meta[name="_csrf"]');
    const headerMeta = document.querySelector('meta[name="_csrf_header"]');
    if (!tokenMeta || !headerMeta) {
      return null;
    }
    return { header: headerMeta.getAttribute("content"), token: tokenMeta.getAttribute("content") };
  }

  function applyState(button, liked) {
    const icon = button.querySelector(".js-like-icon");
    button.classList.toggle("active", liked);
    if (!icon) {
      return;
    }
    if (liked) {
      icon.classList.remove("ri-heart-line", "text-gray-400");
      icon.classList.add("ri-heart-fill", "text-red-600");
    } else {
      icon.classList.remove("ri-heart-fill", "text-red-600");
      icon.classList.add("ri-heart-line", "text-gray-400");
    }
  }

  function bind(button) {
    button.addEventListener("click", async function () {
      const productId = button.dataset.productId;
      if (!productId || button.dataset.loading === "1") {
        return;
      }
      button.dataset.loading = "1";

      const headers = { "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8" };
      const c = csrf();
      if (c && c.header) {
        headers[c.header] = c.token;
      }

      try {
        const res = await fetch("/likes/toggle", {
          method: "POST",
          headers: headers,
          body: "productId=" + encodeURIComponent(productId),
        });
        // CSRF トークン失効・セッション切れ（maximumSessions による無効化含む）は
        // Spring が 403 + HTML を返すため、JSON パース前に検知してログインへ誘導する。
        if (res.status === 403) {
          window.location.href = "/login";
          return;
        }
        if (!res.ok) {
          console.warn("like toggle http error:", res.status);
          return;
        }
        const data = await res.json();

        if (data.response === "no_login") {
          window.location.href = "/login";
          return;
        }
        if (data.response === "add") {
          applyState(button, true);
        } else if (data.response === "remove") {
          applyState(button, false);
        } else {
          console.warn("like toggle error:", data.message);
        }
      } catch (e) {
        console.error("like toggle failed", e);
      } finally {
        button.dataset.loading = "";
      }
    });
  }

  document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll(".js-click-like").forEach(bind);
  });
})();
