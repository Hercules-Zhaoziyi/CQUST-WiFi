const isMac = navigator.userAgent.includes("Macintosh");
document.documentElement.dataset.platform = isMac ? "macos" : "windows";
if (isMac) {
  document.addEventListener("DOMContentLoaded", () => {
    const hideButton = document.getElementById("to-tray");
    hideButton.title = "隐藏窗口，可从菜单栏或 Dock 重新打开";
    hideButton.setAttribute("aria-label", "隐藏窗口");
    document.querySelector("#startup").closest(".setting").querySelector("p").textContent = "登录系统后启动，并隐藏窗口";
    const note = document.querySelector(".settings-note p");
    note.replaceChildren("关闭窗口后应用仍在后台运行，可从菜单栏或 Dock 重新打开。", document.createElement("br"), "完全退出请使用菜单栏图标的“退出软件”。");
  });
}
