var XG_DEBUG_OPEN = typeof window !== "undefined" && window.location && window.location.href.indexOf("xgplayerdebugger=1") > -1;
var STYLE = {
  info: "color: #525252; background-color: #90ee90;",
  error: "color: #525252; background-color: red;",
  warn: "color: #525252; background-color: yellow; "
};
var XGTAG = "%c[xgplayer]";
var XG_DEBUG = {
  config: {
    debug: XG_DEBUG_OPEN ? 3 : 0
  },
  logInfo: function logInfo(message) {
    var _console;
    for (var _len = arguments.length, optionalParams = new Array(_len > 1 ? _len - 1 : 0), _key = 1; _key < _len; _key++) {
      optionalParams[_key - 1] = arguments[_key];
    }
    this.config.debug >= 3 && (_console = console).log.apply(_console, [XGTAG, STYLE.info, message].concat(optionalParams));
  },
  logWarn: function logWarn(message) {
    var _console2;
    for (var _len2 = arguments.length, optionalParams = new Array(_len2 > 1 ? _len2 - 1 : 0), _key2 = 1; _key2 < _len2; _key2++) {
      optionalParams[_key2 - 1] = arguments[_key2];
    }
    this.config.debug >= 1 && (_console2 = console).warn.apply(_console2, [XGTAG, STYLE.warn, message].concat(optionalParams));
  },
  logError: function logError(message) {
    var _console3;
    if (this.config.debug < 1) {
      return;
    }
    var _fun = this.config.debug >= 2 ? "trace" : "error";
    for (var _len3 = arguments.length, optionalParams = new Array(_len3 > 1 ? _len3 - 1 : 0), _key3 = 1; _key3 < _len3; _key3++) {
      optionalParams[_key3 - 1] = arguments[_key3];
    }
    (_console3 = console)[_fun].apply(_console3, [XGTAG, STYLE.error, message].concat(optionalParams));
  }
};
function bindDebug(player) {
  player.logInfo = XG_DEBUG.logInfo.bind(player);
  player.logWarn = XG_DEBUG.logWarn.bind(player);
  player.logError = XG_DEBUG.logError.bind(player);
}
export { bindDebug, XG_DEBUG as default };
