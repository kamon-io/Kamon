(function(t){function e(e){for(var r,a,o=e[0],c=e[1],u=e[2],d=0,p=[];d<o.length;d++)a=o[d],s[a]&&p.push(s[a][0]),s[a]=0;for(r in c)Object.prototype.hasOwnProperty.call(c,r)&&(t[r]=c[r]);l&&l(e);while(p.length)p.shift()();return i.push.apply(i,u||[]),n()}function n(){for(var t,e=0;e<i.length;e++){for(var n=i[e],r=!0,o=1;o<n.length;o++){var c=n[o];0!==s[c]&&(r=!1)}r&&(i.splice(e--,1),t=a(a.s=n[0]))}return t}var r={},s={app:0},i=[];function a(e){if(r[e])return r[e].exports;var n=r[e]={i:e,l:!1,exports:{}};return t[e].call(n.exports,n,n.exports,a),n.l=!0,n.exports}a.m=t,a.c=r,a.d=function(t,e,n){a.o(t,e)||Object.defineProperty(t,e,{enumerable:!0,get:n})},a.r=function(t){"undefined"!==typeof Symbol&&Symbol.toStringTag&&Object.defineProperty(t,Symbol.toStringTag,{value:"Module"}),Object.defineProperty(t,"__esModule",{value:!0})},a.t=function(t,e){if(1&e&&(t=a(t)),8&e)return t;if(4&e&&"object"===typeof t&&t&&t.__esModule)return t;var n=Object.create(null);if(a.r(n),Object.defineProperty(n,"default",{enumerable:!0,value:t}),2&e&&"string"!=typeof t)for(var r in t)a.d(n,r,function(e){return t[e]}.bind(null,r));return n},a.n=function(t){var e=t&&t.__esModule?function(){return t["default"]}:function(){return t};return a.d(e,"a",e),e},a.o=function(t,e){return Object.prototype.hasOwnProperty.call(t,e)},a.p="/";var o=window["webpackJsonp"]=window["webpackJsonp"]||[],c=o.push.bind(o);o.push=e,o=o.slice();for(var u=0;u<o.length;u++)e(o[u]);var l=c;i.push([0,"chunk-vendors"]),n()})({0:function(t,e,n){t.exports=n("cd49")},"168f":function(t,e,n){},"5c0b":function(t,e,n){"use strict";var r=n("5e27"),s=n.n(r);s.a},"5e27":function(t,e,n){},"692c":function(t,e,n){},7765:function(t,e,n){"use strict";var r=n("168f"),s=n.n(r);s.a},8098:function(t,e,n){},"9b19":function(t,e,n){t.exports=n.p+"img/logo.592500c9.svg"},ad71:function(t,e,n){},ad9d:function(t,e,n){"use strict";var r=n("8098"),s=n.n(r);s.a},ae86:function(t,e,n){},c5e5:function(t,e,n){"use strict";var r=n("ad71"),s=n.n(r);s.a},ca31:function(t,e,n){"use strict";var r=n("692c"),s=n.n(r);s.a},cd49:function(t,e,n){"use strict";n.r(e);var r,s=n("2b0e"),i=function(){var t=this,e=t.$createElement,n=t._self._c||e;return n("div",{attrs:{id:"app"}},[t._m(0),n("router-view")],1)},a=[function(){var t=this,e=t.$createElement,r=t._self._c||e;return r("div",{staticClass:"header w-100 mb-1 sticky-top"},[r("div",{staticClass:"container h-100"},[r("div",{staticClass:"row h-100 justify-content-between"},[r("div",{staticClass:"col-auto h-100"},[r("img",{staticClass:"logo h-100 img-fluid",attrs:{src:n("9b19"),alt:""}})])])])])}],o=(n("5c0b"),n("2877")),c={},u=Object(o["a"])(c,i,a,!1,null,null,null),l=u.exports,d=n("8c4f"),p=function(){var t=this,e=t.$createElement,n=t._self._c||e;return n("div",{staticClass:"container"},[n("div",{staticClass:"row"},[n("div",{staticClass:"col-12"},[n("overview-card",{attrs:{"module-registry":t.moduleRegistry,"metric-registry":t.metricRegistry,instrumentation:t.instrumentation}})],1),n("div",{staticClass:"col-12"},[n("environment-card",{attrs:{environment:t.environment}})],1),n("div",{staticClass:"col-12"},[n("module-list",{attrs:{modules:t.modules}})],1),t.metrics.length>0?n("div",{staticClass:"col-12 pt-4 pb-2"},[n("h2",[t._v("Metrics")])]):t._e(),t.metrics.length>0?n("div",{staticClass:"col-12"},[n("metric-list",{attrs:{metrics:t.metrics}})],1):t._e(),n("div",{staticClass:"col-12 mb-5"},[n("instrumentation-module-list",{attrs:{modules:t.instrumentationModules}})],1)])])},f=[],m=n("9ab4"),b=n("60a3"),v=n("1b15"),g=function(){var t=this,e=t.$createElement,n=t._self._c||e;return n("div",{staticClass:"w-100"},[n("status-section",{attrs:{title:"Reporters"}},[n("div",{staticClass:"row"},[t._l(t.reporterModules,function(t){return n("div",{key:t.name,staticClass:"col-12 py-1"},[n("module-status-card",{attrs:{module:t}})],1)}),t.hasApmModule?t._e():n("div",{staticClass:"col-12 py-1 apm-suggestion"},[n("a",{attrs:{href:"https://kamon.io/apm/?utm_source=kamon&utm_medium=status-page&utm_campaign=kamon-status",target:"_blank"}},[n("module-status-card",{attrs:{"is-suggestion":!0,module:t.apmModuleSuggestion}})],1)])],2)]),t.plainModules.length>0?n("status-section",{attrs:{title:"Modules"}},[n("div",{staticClass:"row"},t._l(t.plainModules,function(t){return n("div",{key:t.name,staticClass:"col-12 py-1"},[n("module-status-card",{attrs:{module:t}})],1)}),0)]):t._e()],1)},h=[],y=n("bc3a"),_=n.n(y);(function(t){t["Combined"]="combined",t["Metric"]="metric",t["Span"]="span",t["Plain"]="plain",t["Unknown"]="unknown"})(r||(r={}));var C=function(){function t(){}return t.settings=function(){return _.a.get("/status/settings").then(function(t){var e=JSON.parse(t.data.config);return{version:t.data.version,environment:t.data.environment,config:e}})},t.moduleRegistryStatus=function(){return _.a.get("/status/modules").then(function(t){return t.data})},t.metricRegistryStatus=function(){return _.a.get("/status/metrics").then(function(t){var e=t.data,n=function(t,e){return t+":"+e+" "};return e.metrics.forEach(function(t){"rangeSampler"===t.type&&(t.type="Range Sampler");var e="";t.instruments.forEach(function(t){Object.keys(t).forEach(function(r){e+=n(r,t[r])})}),t.search=n("name",t.name.toLowerCase())+n("type",t.type.toLowerCase())+e}),e})},t.instrumentationStatus=function(){return _.a.get("/status/instrumentation").then(function(t){var e={present:t.data.present,modules:[],errors:t.data.errors},n=t.data.modules;return Object.keys(n).forEach(function(t){var r=n[t];e.modules.push(m["a"]({name:t},r))}),e})},t}(),O=function(){var t=this,e=t.$createElement,n=t._self._c||e;return n("status-card",{attrs:{"indicator-text":t.runStatus.message,"indicator-icon":t.runStatus.icon,"indicator-background-color":t.runStatus.color}},[n("div",{staticClass:"py-3 pl-4",attrs:{slot:"default"},slot:"default"},[n("h5",{staticClass:"mb-0 mr-3 d-inline-block"},[t._v(t._s(t.module.name))]),t.isSuggestion?t._e():n("div",{staticClass:"tag-container d-inline-block"},[n("span",{staticClass:"tag"},[t._v(t._s(t.status))])]),n("div",{staticClass:"text-label"},[t._v("\n      "+t._s(t.module.description)+"\n    ")])])])},j=[],x=function(){var t=this,e=t.$createElement,n=t._self._c||e;return n("card",[n("div",{staticClass:"row status-card no-gutters"},[n("div",{staticClass:"col-auto"},[n("div",{staticClass:"status-indicator-wrap text-center text-uppercase",style:t.indicatorStyle},[t._t("status-indicator",[n("div",{staticClass:"status-indicator h-100"},[n("i",{staticClass:"fas fa-fw",class:t.indicatorIcon})])])],2)]),n("div",{staticClass:"col"},[t._t("default")],2)])])},k=[],w=function(){var t=this,e=t.$createElement,n=t._self._c||e;return n("div",{staticClass:"card-wrapper"},[t._t("default")],2)},M=[],S=function(t){function e(){return null!==t&&t.apply(this,arguments)||this}return m["c"](e,t),e=m["b"]([b["a"]],e),e}(b["c"]),P=S,R=P,E=(n("e60d"),Object(o["a"])(R,w,M,!1,null,null,null)),$=E.exports,T=function(t){function e(){return null!==t&&t.apply(this,arguments)||this}return m["c"](e,t),Object.defineProperty(e.prototype,"indicatorStyle",{get:function(){return{color:this.indicatorColor,backgroundColor:this.indicatorBackgroundColor}},enumerable:!0,configurable:!0}),m["b"]([Object(b["b"])({default:"white"})],e.prototype,"indicatorColor",void 0),m["b"]([Object(b["b"])({default:"#989898"})],e.prototype,"indicatorBackgroundColor",void 0),m["b"]([Object(b["b"])({default:"fa-question"})],e.prototype,"indicatorIcon",void 0),m["b"]([Object(b["b"])({default:"Unknown"})],e.prototype,"indicatorText",void 0),e=m["b"]([Object(b["a"])({components:{card:$}})],e),e}(b["c"]),U=T,I=U,A=(n("7765"),Object(o["a"])(I,x,k,!1,null,null,null)),N=A.exports,z=function(t){function e(){return null!==t&&t.apply(this,arguments)||this}return m["c"](e,t),Object.defineProperty(e.prototype,"status",{get:function(){return this.module.started?"started":this.module.enabled?"present":"disabled"},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"runStatus",{get:function(){return this.isSuggestion?{message:"suggested",color:"#5fd7cc",icon:"fa-plug"}:this.module.enabled?this.module.started?{message:"active",color:"#7ade94",icon:"fa-check"}:{message:"available",color:"#bbbbbb",icon:"fa-check"}:{message:"disabled",color:"#ff9898",icon:"fa-stop-circle"}},enumerable:!0,configurable:!0}),m["b"]([Object(b["b"])({default:!1})],e.prototype,"isSuggestion",void 0),m["b"]([Object(b["b"])()],e.prototype,"module",void 0),e=m["b"]([Object(b["a"])({components:{"status-card":N}})],e),e}(b["c"]),D=z,J=D,B=Object(o["a"])(J,O,j,!1,null,null,null),K=B.exports,L=function(){var t=this,e=t.$createElement,n=t._self._c||e;return n("div",{staticClass:"row"},[n("div",{staticClass:"col-12 pt-4 pb-2"},[n("h3",[t._v(t._s(t.title))])]),n("div",{staticClass:"col-12 py-1"},[t._t("default")],2)])},q=[],H=function(t){function e(){return null!==t&&t.apply(this,arguments)||this}return m["c"](e,t),m["b"]([Object(b["b"])()],e.prototype,"title",void 0),e=m["b"]([b["a"]],e),e}(b["c"]),F=H,G=F,Q=Object(o["a"])(G,L,q,!1,null,null,null),V=Q.exports,W=function(t){function e(){var e=null!==t&&t.apply(this,arguments)||this;return e.apmModuleSuggestion={name:"Kamon APM",description:"See your metrics and trace data for free with a Starter account.",kind:r.Combined,programmaticallyRegistered:!1,enabled:!1,started:!1,clazz:""},e}return m["c"](e,t),Object.defineProperty(e.prototype,"sortedModules",{get:function(){return this.modules.sort(function(t,e){return t.started===e.started?t.name.localeCompare(e.name):t.started?-1:1})},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"reporterModules",{get:function(){return this.sortedModules.filter(this.isReporter)},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"plainModules",{get:function(){var t=this;return this.sortedModules.filter(function(e){return!t.isReporter(e)})},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"hasApmModule",{get:function(){var t=["kamon.apm.KamonApm"];return void 0!==this.modules.find(function(e){return t.includes(e.clazz)})},enumerable:!0,configurable:!0}),e.prototype.isReporter=function(t){return[r.Combined,r.Span,r.Metric].includes(t.kind)},e.prototype.isStarted=function(t){return t.started},m["b"]([Object(b["b"])()],e.prototype,"modules",void 0),e=m["b"]([Object(b["a"])({components:{"status-section":V,"module-status-card":K}})],e),e}(b["c"]),X=W,Y=X,Z=Object(o["a"])(Y,g,h,!1,null,null,null),tt=Z.exports,et=function(){var t=this,e=t.$createElement,n=t._self._c||e;return n("div",{staticClass:"row"},[t.modules.length>0?n("div",{staticClass:"col-12 pt-4 pb-2"},[n("h2",[t._v("Instrumentation Modules")])]):t._e(),t._l(t.sortedModules,function(t){return n("div",{key:t.name,staticClass:"col-12 py-1"},[n("instrumentation-module-status-card",{attrs:{module:t}})],1)})],2)},nt=[],rt=function(){var t=this,e=t.$createElement,n=t._self._c||e;return n("status-card",{attrs:{"indicator-text":t.runStatus.message,"indicator-icon":t.runStatus.icon,"indicator-background-color":t.runStatus.color}},[n("div",{staticClass:"py-3 pl-4",attrs:{slot:"default"},slot:"default"},[n("h5",{staticClass:"mb-0 mr-3 d-inline-block"},[t._v(t._s(t.module.name))]),n("div",{staticClass:"text-label"},[t._v("\n      "+t._s(t.module.description)+"\n    ")])])])},st=[],it=function(t){function e(){return null!==t&&t.apply(this,arguments)||this}return m["c"](e,t),Object.defineProperty(e.prototype,"status",{get:function(){return this.module.active?"active":this.module.enabled?"present":"disabled"},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"runStatus",{get:function(){return this.module.enabled?this.module.active?{message:"active",color:"#7ade94",icon:"fa-check"}:{message:"available",color:"#bbbbbb",icon:"fa-power-off"}:{message:"disabled",color:"#ff9898",icon:"fa-stop-circle"}},enumerable:!0,configurable:!0}),m["b"]([Object(b["b"])()],e.prototype,"module",void 0),e=m["b"]([Object(b["a"])({components:{"status-card":N}})],e),e}(b["c"]),at=it,ot=at,ct=Object(o["a"])(ot,rt,st,!1,null,null,null),ut=ct.exports,lt=function(t){function e(){return null!==t&&t.apply(this,arguments)||this}return m["c"](e,t),Object.defineProperty(e.prototype,"sortedModules",{get:function(){return this.modules.sort(function(t,e){return t.active===e.active?t.name.localeCompare(e.name):t.active?-1:1})},enumerable:!0,configurable:!0}),m["b"]([Object(b["b"])()],e.prototype,"modules",void 0),e=m["b"]([Object(b["a"])({components:{"instrumentation-module-status-card":ut}})],e),e}(b["c"]),dt=lt,pt=dt,ft=(n("c5e5"),Object(o["a"])(pt,et,nt,!1,null,null,null)),mt=ft.exports,bt=function(){var t=this,e=t.$createElement,n=t._self._c||e;return n("div",{staticClass:"row no-gutters"},[n("div",{staticClass:"col-12"},[n("div",{staticClass:"search-box mb-3"},[t._m(0),n("input",{directives:[{name:"model",rawName:"v-model",value:t.filterPattern,expression:"filterPattern"}],staticClass:"w-100",attrs:{type:"text"},domProps:{value:t.filterPattern},on:{input:function(e){e.target.composing||(t.filterPattern=e.target.value)}}}),n("span",{staticClass:"search-stats"},[t._v(t._s(t.searchStats))])])]),t.matchedMetrics.length>0?n("div",{staticClass:"col-12"},t._l(t.matchedMetrics,function(e,r){return n("div",{key:e.name,staticClass:"row no-gutters"},[n("div",{staticClass:"col-12"},[n("metric-list-item",{attrs:{metric:e}})],1),r<t.metrics.length-1?n("hr",{staticClass:"w-100"}):t._e()])}),0):t._e()])},vt=[function(){var t=this,e=t.$createElement,n=t._self._c||e;return n("span",{staticClass:"search-icon"},[n("i",{staticClass:"fas fa-search fa-fw fa-flip-horizontal"})])}],gt=function(){var t=this,e=t.$createElement,n=t._self._c||e;return n("status-card",{staticClass:"metric-list-item c-pointer my-1",class:{"my-4":t.expanded},attrs:{"indicator-background-color":"#7ade94"}},[n("div",{attrs:{slot:"status-indicator"},on:{click:t.onCardClick},slot:"status-indicator"},[n("div",{staticClass:"metric-count"},[t._v("\n      "+t._s(t.metric.instruments.length)+"\n    ")]),n("div",{staticClass:"indicator-label"},[t._v("INSTANCES")])]),n("div",{attrs:{slot:"default"},on:{click:t.onCardClick},slot:"default"},[n("div",{staticClass:"row no-gutters"},[n("div",{staticClass:"col"},[n("div",{staticClass:"py-3 pl-4"},[n("h5",{staticClass:"mb-0 mr-3 d-inline-block"},[t._v(t._s(t.metric.name))]),n("span",{staticClass:"tag"},[t._v(t._s(t.metric.instrumentType))]),"none"!==t.metric.unitMagnitude?n("span",{staticClass:"tag"},[t._v(t._s(t.metric.unitMagnitude))]):t._e(),n("div",{staticClass:"text-label"},[n("span",[t._v(t._s(t.metric.description))])])])]),n("div",{staticClass:"col-auto expansion-icon px-5"},[n("i",{staticClass:"fas fa-fw",class:t.expansionIcon})]),t.expanded?n("div",{staticClass:"col-12 series-container"},t._l(t.metric.instruments,function(e,r){return n("div",{key:r},[n("div",{staticClass:"p-3"},[n("h6",[t._v("Instance #"+t._s(r+1))]),n("div",{staticClass:"tag-container"},[t._l(Object.keys(e),function(r){return n("span",{key:r,staticClass:"tag"},[t._v("\n                "+t._s(r)+"="),n("span",{staticClass:"tag-value"},[t._v(t._s(e[r]))])])}),0===Object.keys(e).length?n("span",{staticClass:"pl-2"},[t._v("No Tags")]):t._e()],2)]),r<t.metric.instruments.length-1?n("hr",{staticClass:"w-100 instance-hr"}):t._e()])}),0):t._e()])])])},ht=[],yt=function(t){function e(){var e=null!==t&&t.apply(this,arguments)||this;return e.expanded=!1,e}return m["c"](e,t),Object.defineProperty(e.prototype,"expansionIcon",{get:function(){return this.expanded?"fa-angle-up":"fa-angle-down"},enumerable:!0,configurable:!0}),e.prototype.onCardClick=function(){this.expanded=!this.expanded},m["b"]([Object(b["b"])({default:null})],e.prototype,"metric",void 0),e=m["b"]([Object(b["a"])({components:{"status-card":N}})],e),e}(b["c"]),_t=yt,Ct=_t,Ot=(n("ad9d"),Object(o["a"])(Ct,gt,ht,!1,null,null,null)),jt=Ot.exports,xt=function(t){function e(){var e=null!==t&&t.apply(this,arguments)||this;return e.filterPattern="",e}return m["c"](e,t),Object.defineProperty(e.prototype,"totalMetrics",{get:function(){return this.metrics.length},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"filterRegex",{get:function(){return new RegExp(this.filterPattern)},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"searchStats",{get:function(){return this.filterPattern.length>0?"showing "+this.matchedMetrics.length+" out of "+this.totalMetrics+" metrics":this.totalMetrics+" metrics"},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"matchedMetrics",{get:function(){var t=this;return this.filterPattern.length>0?this.metrics.filter(function(e){return null!=e.search.match(t.filterRegex)}):this.metrics},enumerable:!0,configurable:!0}),m["b"]([Object(b["b"])({default:[]})],e.prototype,"metrics",void 0),e=m["b"]([Object(b["a"])({components:{card:$,"status-card":N,"metric-list-item":jt}})],e),e}(b["c"]),kt=xt,wt=kt,Mt=(n("ca31"),Object(o["a"])(wt,bt,vt,!1,null,null,null)),St=Mt.exports,Pt=function(){var t=this,e=t.$createElement,n=t._self._c||e;return n("status-section",{attrs:{title:"Environment"}},[n("card",[n("div",{staticClass:"row py-2 no-gutters"},[n("div",{staticClass:"col-auto py-2 px-3"},[n("div",{staticClass:"text-uppercase text-label pb-1"},[t._v("Service")]),n("h6",[t._v(t._s(t.service))])]),n("div",{staticClass:"col-auto py-2 px-3"},[n("div",{staticClass:"text-uppercase text-label pb-1"},[t._v("Host")]),n("h6",[t._v(t._s(t.host))])]),n("div",{staticClass:"col-auto py-2 px-3"},[n("div",{staticClass:"text-uppercase text-label pb-1"},[t._v("instance")]),n("h6",[t._v(t._s(t.instance))])]),n("div",{staticClass:"col-12 col-md-3 py-2 px-3"},[n("div",{staticClass:"text-uppercase text-label pb-1"},[t._v("tags")]),Object.keys(t.environmentTags).length>0?n("div",{staticClass:"tag-container"},t._l(Object.keys(t.environmentTags),function(e){return n("span",{key:e,staticClass:"tag"},[t._v("\n            "+t._s(e)+"="),n("span",{staticClass:"tag-value"},[t._v(t._s(t.environmentTags[e]))])])}),0):n("div",[n("h6",[t._v("None")])])])])])],1)},Rt=[],Et=function(t){function e(){var e=null!==t&&t.apply(this,arguments)||this;return e.environment=v["none"],e}return m["c"](e,t),Object.defineProperty(e.prototype,"instance",{get:function(){return this.environment.map(function(t){return t.instance}).getOrElse("Unknown")},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"host",{get:function(){return this.environment.map(function(t){return t.host}).getOrElse("Unknown")},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"service",{get:function(){return this.environment.map(function(t){return t.service}).getOrElse("Unknown")},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"environmentTags",{get:function(){return this.environment.map(function(t){return t.tags}).getOrElse({})},enumerable:!0,configurable:!0}),m["b"]([Object(b["b"])()],e.prototype,"environment",void 0),e=m["b"]([Object(b["a"])({components:{card:$,"status-section":V}})],e),e}(b["c"]),$t=Et,Tt=$t,Ut=Object(o["a"])(Tt,Pt,Rt,!1,null,null,null),It=Ut.exports,At=function(){var t=this,e=t.$createElement,n=t._self._c||e;return n("status-section",{attrs:{title:"Overview"}},[n("card",[n("div",{staticClass:"row py-2 no-gutters"},[n("div",{staticClass:"col-12 col-md-3 py-2 px-3"},[n("div",{staticClass:"text-uppercase text-label pb-1"},[t._v("Instrumentation")]),n("h5",[t._v(t._s(t.instrumentationStatusMessage))])]),n("div",{staticClass:"col-12 col-md-3 py-2 px-3"},[n("div",{staticClass:"text-uppercase text-label pb-1"},[t._v("Reporters")]),n("h5",[t._v(t._s(t.activeReporters.length)+" Started")])]),n("div",{staticClass:"col-12 col-md-3 py-2 px-3"},[n("div",{staticClass:"text-uppercase text-label pb-1"},[t._v("Metrics")]),n("h5",[t._v(t._s(t.metricsStatusMessage))])])])])],1)},Nt=[],zt=function(t){function e(){var e=null!==t&&t.apply(this,arguments)||this;return e.moduleRegistry=v["none"],e.metricRegistry=v["none"],e.instrumentation=v["none"],e}return m["c"](e,t),Object.defineProperty(e.prototype,"reporterModules",{get:function(){var t=this;return this.moduleRegistry.map(function(e){return e.modules.filter(t.isReporter)}).getOrElse([])},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"activeReporters",{get:function(){return this.reporterModules.filter(this.isStarted)},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"trackedMetrics",{get:function(){return this.metricRegistry.map(function(t){return t.metrics.length})},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"instrumentationStatusMessage",{get:function(){return this.instrumentation.map(function(t){return t.present?"Active":"Disabled"}).getOrElse("Unknown")},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"metricsStatusMessage",{get:function(){return this.trackedMetrics.map(function(t){return t+" Metrics"}).getOrElse("Unknown")},enumerable:!0,configurable:!0}),e.prototype.isReporter=function(t){return[r.Combined,r.Span,r.Metric].includes(t.kind)},e.prototype.isStarted=function(t){return t.started},m["b"]([Object(b["b"])()],e.prototype,"moduleRegistry",void 0),m["b"]([Object(b["b"])()],e.prototype,"metricRegistry",void 0),m["b"]([Object(b["b"])()],e.prototype,"instrumentation",void 0),e=m["b"]([Object(b["a"])({components:{card:$,"status-section":V}})],e),e}(b["c"]),Dt=zt,Jt=Dt,Bt=Object(o["a"])(Jt,At,Nt,!1,null,null,null),Kt=Bt.exports,Lt=function(t){function e(){var e=null!==t&&t.apply(this,arguments)||this;return e.settings=v["none"],e.moduleRegistry=v["none"],e.metricRegistry=v["none"],e.instrumentation=v["none"],e}return m["c"](e,t),Object.defineProperty(e.prototype,"reporterModules",{get:function(){var t=this;return this.moduleRegistry.map(function(e){return e.modules.filter(t.isReporter)}).getOrElse([])},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"activeReporters",{get:function(){return this.reporterModules.filter(this.isStarted)},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"plainModules",{get:function(){var t=this;return this.moduleRegistry.map(function(e){return e.modules.filter(function(e){return!t.isReporter(e)})}).getOrElse([])},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"trackedMetrics",{get:function(){return this.metricRegistry.map(function(t){return t.metrics.length})},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"instrumentationStatusMessage",{get:function(){return this.instrumentation.map(function(t){return t.present?"Active":"Disabled"}).getOrElse("Unknown")},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"metricsStatusMessage",{get:function(){return this.trackedMetrics.map(function(t){return t+" Tracked"}).getOrElse("Unknown")},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"metrics",{get:function(){return this.metricRegistry.map(function(t){return t.metrics}).getOrElse([])},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"modules",{get:function(){return this.moduleRegistry.map(function(t){return t.modules}).getOrElse([])},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"instrumentationModules",{get:function(){return this.instrumentation.map(function(t){return t.modules}).getOrElse([])},enumerable:!0,configurable:!0}),Object.defineProperty(e.prototype,"environment",{get:function(){return this.settings.map(function(t){return t.environment})},enumerable:!0,configurable:!0}),e.prototype.mounted=function(){this.refreshData()},e.prototype.refreshData=function(){var t=this;C.settings().then(function(e){t.settings=Object(v["some"])(e)}),C.metricRegistryStatus().then(function(e){t.metricRegistry=Object(v["some"])(e)}),C.moduleRegistryStatus().then(function(e){t.moduleRegistry=Object(v["some"])(e)}),C.instrumentationStatus().then(function(e){t.instrumentation=Object(v["some"])(e)})},e.prototype.isReporter=function(t){return[r.Combined,r.Span,r.Metric].includes(t.kind)},e.prototype.isStarted=function(t){return t.started},e=m["b"]([Object(b["a"])({components:{"overview-card":Kt,"module-list":tt,"instrumentation-module-list":mt,"metric-list":St,"environment-card":It}})],e),e}(b["c"]),qt=Lt,Ht=qt,Ft=Object(o["a"])(Ht,p,f,!1,null,null,null),Gt=Ft.exports;s["default"].use(d["a"]);var Qt=new d["a"]({routes:[{path:"/",name:"overview",component:Gt}]});n("ab8b"),n("becf"),n("fb98");s["default"].config.productionTip=!1,new s["default"]({router:Qt,render:function(t){return t(l)}}).$mount("#app")},e60d:function(t,e,n){"use strict";var r=n("ae86"),s=n.n(r);s.a},fb98:function(t,e,n){}});