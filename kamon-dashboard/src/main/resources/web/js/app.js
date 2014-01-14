'use strict';

angular.module('dashboard', ['dashboard.services','ui.bootstrap','nvd3ChartDirectives']);

//funtion to fade left panel
$(document).ready(function() {
    $(".alert").addClass("in").fadeOut(4500);
});

