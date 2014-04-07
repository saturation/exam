(function () {
    'use strict';
    angular.module("sitnet.controllers")
        .controller('DatepickerCtrl', ['$scope', 'dateService',
            function ($scope, dateService) {

                $scope.dateService = dateService;

                $scope.today = function () {
                    $scope.dateService.startDate = new Date();
                    $scope.dateService.endDate = new Date();
                };
                $scope.today();

                $scope.showWeeks = true;
                $scope.toggleWeeks = function () {
                    $scope.showWeeks = !$scope.showWeeks;
                };

                $scope.clear = function () {
                    $scope.dateService.startDate = null;
                    $scope.dateService.endDate = null;
                };

                // Disable weekend selection
                $scope.disabled = function (date, mode) {
                    return ( mode === 'day' && ( date.getDay() === 0 || date.getDay() === 6 ) );
                };

                $scope.toggleMin = function () {
                    $scope.minDate = ( $scope.minDate ) ? null : new Date();
                };
                $scope.toggleMin();

                $scope.openStartDate = function ($event) {
                    $event.preventDefault();
                    $event.stopPropagation();

                    $scope.startDateOpened = true;
                };

                $scope.openEndDate = function ($event) {
                    $event.preventDefault();
                    $event.stopPropagation();

                    $scope.endDateOpened = true;
                };

                $scope.$watch('dateService.startDate', function (v) {
                    var d = new Date(v);
                    var curr_date = d.getDate();
                    var curr_month = d.getMonth() + 1; //Months are zero based
                    var curr_year = d.getFullYear();
                    $scope.dateService.modStartDate = curr_date + "-" + curr_month + "-" + curr_year;
                    $scope.dateService.startTimestamp = d.getTime();
                });

                $scope.$watch('dateService.endDate', function (v) {
                    var d = new Date(v);
                    var curr_date = d.getDate();
                    var curr_month = d.getMonth() + 1; //Months are zero based
                    var curr_year = d.getFullYear();
                    $scope.dateService.modEndDate = curr_date + "-" + curr_month + "-" + curr_year;
                    $scope.dateService.endTimestamp = d.getTime();
                    
//                    var myDate="26-02-2012";
//                    myDate=myDate.split("-");
//                    var newDate=myDate[1]+"/"+myDate[0]+"/"+myDate[2];
//                    alert(new Date(newDate).getTime());
                    
                });

                $scope.dateOptions = {
                    'starting-day': 1
                };

                $scope.formats = ['dd-MM-yyyy', 'yyyy/MM/dd', 'shortDate'];
                $scope.format = $scope.formats[0];
            }])
}());
