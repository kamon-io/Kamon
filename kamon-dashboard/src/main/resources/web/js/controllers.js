'use strict';

function kamonCtrl($scope, ActorSystemMetrics) {

  $scope.actorSystemMetrics = [];

  $scope.startPolling = function() {
      setInterval(function() {
          $scope.getActorSystemMetrics()
      }, 3000);
  }

  $scope.getActorSystemMetrics = function() {
    var actorSystemMetrics = ActorSystemMetrics.query(function() {
      $scope.actorSystemMetrics = actorSystemMetrics;   //.concat($scope.actorSystemMetrics);

      //$scope.getActorSystemMetrics();
    });
  }
}

function myctrl($scope, $http) {
}
