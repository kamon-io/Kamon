'use strict';

angular.module('kamonServices', ['ngResource']).factory('ActorSystemMetrics', function($resource) { return $resource('metrics/dispatchers');})