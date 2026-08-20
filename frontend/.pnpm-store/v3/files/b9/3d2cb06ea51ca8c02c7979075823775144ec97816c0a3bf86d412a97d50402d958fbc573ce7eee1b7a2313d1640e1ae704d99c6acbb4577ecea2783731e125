import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, createClass as _createClass, toConsumableArray as _toConsumableArray } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import { DOWNLOAD_SPEED_CHANGE, LOADED_DATA, REPLAY } from "../../events.js";
import "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
var DEFAULT_SPEED_TYPE = "cdn";
var SPEED_TYPE = ["cdn"];
var TestSpeed = /* @__PURE__ */ function(_BasePlugin) {
  _inherits(TestSpeed2, _BasePlugin);
  var _super = _createSuper(TestSpeed2);
  function TestSpeed2() {
    var _this;
    _classCallCheck(this, TestSpeed2);
    for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
      args[_key] = arguments[_key];
    }
    _this = _super.call.apply(_super, [this].concat(args));
    _defineProperty(_assertThisInitialized(_this), "getSpeed", function() {
      var type = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : DEFAULT_SPEED_TYPE;
      if (!_this.speedListCache || !_this.speedListCache[type])
        return 0;
      if (_this.speedListCache[type].length <= 0)
        return 0;
      var total = 0;
      _this.speedListCache[type].map(function(item) {
        total += item;
      });
      return Math.floor(total / _this.speedListCache[type].length);
    });
    _defineProperty(_assertThisInitialized(_this), "startTimer", function() {
      if (util.isMSE(_this.player.video)) {
        return;
      }
      _this.initSpeedList();
      _this.cnt = 0;
      _this.timer = setTimeout(_this.testSpeed, _this.config.testTimeStep);
    });
    _defineProperty(_assertThisInitialized(_this), "initSpeedList", function() {
      _this.speedListCache = {};
      SPEED_TYPE.forEach(function(type) {
        _this.speedListCache[type] = [];
      });
    });
    _defineProperty(_assertThisInitialized(_this), "_onRealSpeedChange", function(data) {
      data.speed && _this.appendList(data.speed, data.type || DEFAULT_SPEED_TYPE);
    });
    _defineProperty(_assertThisInitialized(_this), "testSpeed", function() {
      clearTimeout(_this.timer);
      _this.timer = null;
      if (!_this.player || !_this.config.openSpeed)
        return;
      var _this$config = _this.config, url = _this$config.url, loadSize = _this$config.loadSize, testCnt = _this$config.testCnt, testTimeStep = _this$config.testTimeStep;
      var testSpeedUrl = url + (url.indexOf("?") < 0 ? "?testst=" : "&testst=") + Date.now();
      if (_this.cnt >= testCnt) {
        return;
      }
      _this.cnt++;
      try {
        var start = new Date().getTime();
        var end = null;
        var xhr = new XMLHttpRequest();
        _this.xhr = xhr;
        xhr.open("GET", testSpeedUrl);
        var headers = {};
        var random = Math.floor(Math.random() * 10);
        headers.Range = "bytes=" + random + "-" + (loadSize + random);
        if (headers) {
          Object.keys(headers).forEach(function(k) {
            xhr.setRequestHeader(k, headers[k]);
          });
        }
        xhr.onreadystatechange = function() {
          if (xhr.readyState === 4) {
            _this.xhr = null;
            end = new Date().getTime();
            var size = xhr.getResponseHeader("Content-Length") / 1024 * 8;
            var speed = Math.round(size * 1e3 / (end - start));
            _this.appendList(speed);
            _this.timer = setTimeout(_this.testSpeed, testTimeStep);
          }
        };
        xhr.send();
      } catch (e) {
        console.error(e);
      }
    });
    _defineProperty(_assertThisInitialized(_this), "appendList", function(speed) {
      var type = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : DEFAULT_SPEED_TYPE;
      if (!_this.speedListCache || !_this.speedListCache[type])
        return;
      var saveSpeedMax = _this.config.saveSpeedMax;
      if (_this.speedListCache[type].length >= saveSpeedMax) {
        _this.speedListCache[type].shift();
      }
      _this.speedListCache[type].push(speed);
      var _assertThisInitialize = _assertThisInitialized(_this), player = _assertThisInitialize.player;
      if (player) {
        type === DEFAULT_SPEED_TYPE ? player.realTimeSpeed = speed : player[_this.getSpeedName("realTime", type)] = speed;
      }
      _this.updateSpeed(type);
    });
    _defineProperty(_assertThisInitialized(_this), "updateSpeed", function() {
      var type = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : DEFAULT_SPEED_TYPE;
      var speed = _this.getSpeed(type);
      var _assertThisInitialize2 = _assertThisInitialized(_this), player = _assertThisInitialize2.player;
      if (player) {
        if (type === DEFAULT_SPEED_TYPE) {
          if (!player.avgSpeed || speed !== player.avgSpeed) {
            player.avgSpeed = speed;
            player.emit(DOWNLOAD_SPEED_CHANGE, {
              speed,
              realTimeSpeed: player.realTimeSpeed
            });
          }
        } else {
          var speedName = _this.getSpeedName("avg", type);
          if (!player[speedName] || speed !== player[speedName]) {
            player[speedName] = speed;
            player.emit(DOWNLOAD_SPEED_CHANGE, {
              speed,
              realTimeSpeed: player.realTimeSpeed
            });
          }
        }
      }
    });
    return _this;
  }
  _createClass(TestSpeed2, [{
    key: "afterCreate",
    value: function afterCreate() {
      var _this$config2 = this.config, openSpeed = _this$config2.openSpeed, addSpeedTypeList = _this$config2.addSpeedTypeList;
      if ((addSpeedTypeList === null || addSpeedTypeList === void 0 ? void 0 : addSpeedTypeList.length) > 0) {
        SPEED_TYPE.push.apply(SPEED_TYPE, _toConsumableArray(addSpeedTypeList));
      }
      this.initSpeedList();
      this.on("real_time_speed", this._onRealSpeedChange);
      this.timer = null;
      this.cnt = 0;
      this.xhr = null;
      if (!openSpeed) {
        return;
      }
      this.on([LOADED_DATA, REPLAY], this.startTimer);
    }
  }, {
    key: "getSpeedName",
    value: function getSpeedName(namePrefix, type) {
      return namePrefix + "Speed" + type.toUpperCase();
    }
  }, {
    key: "openSpeed",
    get: function get() {
      return this.config.openSpeed;
    },
    set: function set(value) {
      this.config.openSpeed = value;
      if (!value && this.timer) {
        clearTimeout(this.timer);
        this.timer = null;
        return;
      }
      if (this.config.openSpeed) {
        if (this.timer)
          return;
        this.timer = setTimeout(this.testSpeed, this.config.testTimeStep);
      }
    }
  }, {
    key: "destroy",
    value: function destroy() {
      var _this2 = this;
      this.off("real_time_speed", this._onRealSpeedChange);
      this.off([LOADED_DATA, REPLAY], this.startTimer);
      SPEED_TYPE.forEach(function(type) {
        _this2.speedListCache && _this2.speedListCache[type] && (_this2.speedListCache[type] = []);
      });
      this.speedListCache && (this.speedListCache = {});
      clearTimeout(this.timer);
      this.timer = null;
      if (this.xhr && this.xhr.readyState !== 4) {
        this.xhr.cancel && this.xhr.cancel();
        this.xhr = null;
      }
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "testspeed";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        openSpeed: false,
        testCnt: 3,
        loadSize: 200 * 1024,
        testTimeStep: 3e3,
        url: "",
        saveSpeedMax: 5,
        addSpeedTypeList: []
      };
    }
  }]);
  return TestSpeed2;
}(Plugin);
export { TestSpeed as default };
