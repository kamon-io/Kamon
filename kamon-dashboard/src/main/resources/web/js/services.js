'use strict';

var kamonModule = angular.module('kamonServices', ['ngResource']);

kamonModule.factory('ActorSystemMetrics', function($resource) { return $resource('metrics/dispatchers');})
//kamonModule.factory('ActorSystemTree', function($resource) { return $resource('metrics/actorTree');})