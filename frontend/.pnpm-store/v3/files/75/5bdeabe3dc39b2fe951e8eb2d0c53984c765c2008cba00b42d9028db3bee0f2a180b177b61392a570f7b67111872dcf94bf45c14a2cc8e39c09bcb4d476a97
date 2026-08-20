import { createClass as _createClass, classCallCheck as _classCallCheck } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import "../../utils/debug.js";
import "delegate";
var TPL = [{
  tag: "xg-cache",
  className: "xgplayer-progress-cache",
  styleKey: "cachedColor"
}, {
  tag: "xg-played",
  className: "xgplayer-progress-played",
  styleKey: "playedColor"
}];
var InnerList = /* @__PURE__ */ function() {
  function InnerList2(args) {
    _classCallCheck(this, InnerList2);
    this.fragments = args.fragments || [];
    if (this.fragments.length === 0) {
      this.fragments.push({
        percent: 1
      });
    }
    this._callBack = args.actionCallback;
    this.fragConfig = {
      fragFocusClass: args.fragFocusClass || "inner-focus-point",
      fragAutoFocus: !!args.fragAutoFocus,
      fragClass: args.fragClass || ""
    };
    this.style = args.style || {
      playedColor: "",
      cachedColor: "",
      progressColor: ""
    };
    this.duration = 0;
    this.cachedIndex = 0;
    this.playedIndex = 0;
    this.focusIndex = -1;
  }
  _createClass(InnerList2, [{
    key: "updateDuration",
    value: function updateDuration(duration) {
      var _this = this;
      this.duration = duration;
      var start = 0;
      var fragments = this.fragments;
      this.fragments = fragments.map(function(item) {
        item.start = parseInt(start, 10);
        item.end = parseInt(start + item.percent * _this.duration, 10);
        item.duration = parseInt(item.percent * _this.duration, 10);
        start += item.percent * _this.duration;
        return item;
      });
    }
  }, {
    key: "updateProgress",
    value: function updateProgress() {
      var type = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : "played";
      var data = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {
        newIndex: 0,
        curIndex: 0,
        millisecond: 0
      };
      var progressList = this.progressList, fragments = this.fragments;
      if (progressList.length < 1) {
        return;
      }
      var newIndex = data.newIndex, curIndex = data.curIndex, millisecond = data.millisecond;
      if (newIndex !== curIndex) {
        progressList.map(function(item, index) {
          if (index < newIndex) {
            item[type].style.width = "100%";
          } else if (index > newIndex) {
            item[type].style.width = 0;
          }
        });
      }
      var curPFrag = fragments[newIndex];
      var per = millisecond === 0 ? 0 : (millisecond - curPFrag.start) / curPFrag.duration;
      progressList[newIndex][type].style.width = per < 0 ? 0 : "".concat(per * 100, "%");
    }
  }, {
    key: "updateFocus",
    value: function updateFocus(data) {
      if (!this.fragConfig.fragAutoFocus || this.fragments.length < 2) {
        return;
      }
      if (!data) {
        if (this.focusIndex > -1) {
          this.unHightLight(this.focusIndex);
          var _data = {
            index: -1,
            preIndex: this.focusIndex,
            fragment: null
          };
          this._callBack && this._callBack(_data);
          this.focusIndex = -1;
        }
        return;
      }
      var newPIndex = this.findIndex(data.currentTime * 1e3, this.focusIndex);
      if (newPIndex >= 0 && newPIndex !== this.focusIndex) {
        this.focusIndex > -1 && this.unHightLight(this.focusIndex);
        this.setHightLight(newPIndex);
        var _data2 = {
          index: newPIndex,
          preIndex: this.focusIndex,
          fragment: this.fragments[this.focusIndex]
        };
        this.focusIndex = newPIndex;
        this._callBack && this._callBack(_data2);
      }
    }
  }, {
    key: "update",
    value: function update() {
      var data = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {
        cached: 0,
        played: 0
      };
      var duration = arguments.length > 1 ? arguments[1] : void 0;
      if (!this.duration || parseInt(duration * 1e3, 10) !== this.duration) {
        if (!duration && duration !== 0) {
          return;
        }
        this.updateDuration(parseInt(duration * 1e3, 10));
      }
      var playedIndex = this.playedIndex, cachedIndex = this.cachedIndex;
      if (util.typeOf(data.played) !== "Undefined") {
        var newPIndex = this.findIndex(data.played * 1e3, playedIndex);
        if (newPIndex < 0) {
          return;
        }
        this.updateProgress("played", {
          newIndex: newPIndex,
          curIndex: playedIndex,
          millisecond: parseInt(data.played * 1e3, 10)
        });
        this.playedIndex = newPIndex;
      }
      if (util.typeOf(data.cached) !== "Undefined") {
        var newCIndex = this.findIndex(data.cached * 1e3, cachedIndex);
        if (newCIndex < 0) {
          return;
        }
        this.updateProgress("cached", {
          newIndex: newCIndex,
          curIndex: cachedIndex,
          millisecond: parseInt(data.cached * 1e3, 10)
        });
        this.cachedIndex = newCIndex;
      }
    }
  }, {
    key: "findIndex",
    value: function findIndex(time, curIndex) {
      var fragments = this.fragments;
      if (!fragments || fragments.length === 0) {
        return -1;
      }
      if (fragments.length === 1) {
        return 0;
      }
      if (curIndex > -1 && curIndex < fragments.length && time > fragments[curIndex].start && time < fragments[curIndex].end) {
        return curIndex;
      }
      if (time > fragments[fragments.length - 1].start) {
        return fragments.length - 1;
      }
      for (var i = 0; i < fragments.length; i++) {
        if (time > fragments[i].start && time <= fragments[i].end) {
          curIndex = i;
          break;
        }
      }
      return curIndex;
    }
  }, {
    key: "findHightLight",
    value: function findHightLight() {
      var children = this.root.children;
      for (var i = 0; i < children.length; i++) {
        if (util.hasClass(children[i], this.fragConfig.fragFocusClass)) {
          return {
            dom: children[i],
            pos: children[i].getBoundingClientRect()
          };
        }
      }
    }
  }, {
    key: "findFragment",
    value: function findFragment(index) {
      var children = this.root.children;
      if (index < 0 || index >= children.length) {
        return null;
      }
      return {
        dom: children[index],
        pos: children[index].getBoundingClientRect()
      };
    }
  }, {
    key: "unHightLight",
    value: function unHightLight() {
      var children = this.root.children;
      for (var i = 0; i < children.length; i++) {
        util.removeClass(children[i], this.fragConfig.fragFocusClass);
      }
    }
  }, {
    key: "setHightLight",
    value: function setHightLight(index) {
      var children = this.root.children;
      if (index < children.length) {
        util.addClass(children[index], this.fragConfig.fragFocusClass);
        return {
          dom: children[index],
          pos: children[index].getBoundingClientRect()
        };
      }
    }
  }, {
    key: "destroy",
    value: function destroy() {
      this.progressList = null;
      this.fragments = null;
      this.root.innerHTML = "";
    }
  }, {
    key: "reset",
    value: function reset(newOptions) {
      var _this2 = this;
      Object.keys(this.fragConfig).forEach(function(key) {
        if (newOptions[key] !== void 0) {
          _this2.fragConfig[key] = newOptions[key];
        }
      });
      if (newOptions.fragments) {
        this.fragments = newOptions.fragments.length === 0 ? [{
          percent: 1
        }] : newOptions.fragments;
        this.updateDuration(this.duration);
        this.playedIndex = 0;
        this.cachedIndex = 0;
        if (this.root) {
          var _c = this.root.children;
          while (_c.length > 0) {
            this.root.removeChild(_c[0]);
          }
        }
        this.render();
      }
    }
  }, {
    key: "render",
    value: function render() {
      var _this3 = this;
      var progressColor = this.style.progressColor;
      if (!this.root) {
        this.root = util.createDom("xg-inners", "", {}, "progress-list");
      }
      if (this.fragments) {
        var _this$fragConfig = this.fragConfig, fragClass = _this$fragConfig.fragClass, fragFocusClass = _this$fragConfig.fragFocusClass;
        this.progressList = this.fragments.map(function(item) {
          var inner = util.createDom("xg-inner", "", {
            style: progressColor ? "background:".concat(progressColor, "; flex: ").concat(item.percent) : "flex: ".concat(item.percent)
          }, "".concat(item.isFocus ? fragFocusClass : "", " xgplayer-progress-inner ").concat(fragClass));
          _this3.root.appendChild(inner);
          TPL.forEach(function(item2) {
            inner.appendChild(util.createDom(item2.tag, "", {
              style: item2.styleKey ? "background: ".concat(_this3.style[item2.styleKey], "; width:0;") : "width:0;"
            }, item2.className));
          });
          return {
            cached: inner.children[0],
            played: inner.children[1]
          };
        });
      }
      return this.root;
    }
  }]);
  return InnerList2;
}();
export { InnerList as default };
