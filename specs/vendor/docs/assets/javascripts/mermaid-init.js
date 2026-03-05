window.addEventListener("load", function () {
  if (window.mermaid) {
    const isMobile = window.matchMedia("(max-width: 960px)").matches;

    window.mermaid.initialize({
      startOnLoad: true,
      securityLevel: "loose",
      theme: "neutral",
      maxTextSize: 120000,
      themeVariables: {
        fontFamily: "Segoe UI Variable Text, Segoe UI, Selawik, sans-serif",
        fontSize: isMobile ? "13px" : "16px",
        lineColor: "#334155",
        primaryTextColor: "#0f1720",
        secondaryTextColor: "#0f1720"
      },
      flowchart: {
        useMaxWidth: true,
        htmlLabels: true,
        curve: "linear",
        nodeSpacing: isMobile ? 34 : 56,
        rankSpacing: isMobile ? 46 : 78,
        padding: isMobile ? 12 : 20
      },
      sequence: {
        useMaxWidth: true,
        wrap: true,
        actorMargin: isMobile ? 28 : 44,
        messageMargin: isMobile ? 18 : 28,
        diagramMarginX: isMobile ? 18 : 28,
        diagramMarginY: isMobile ? 14 : 22
      },
      gantt: {
        useMaxWidth: true,
        leftPadding: isMobile ? 70 : 100,
        rightPadding: isMobile ? 20 : 30
      },
      er: {
        useMaxWidth: true
      },
      state: {
        useMaxWidth: true
      }
    });
  }
});
