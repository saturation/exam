(function () {
    'use strict';
    angular.module("exam.controllers")
        .controller('LanguageInspectionCtrl', ['$scope', '$translate', '$modal', '$location', 'dialogs', 'EXAM_CONF', 'LanguageInspectionRes',
            function ($scope, $translate, $modal, $location, dialogs, EXAM_CONF, LanguageInspectionRes) {

                $scope.ongoingInspections = [];
                $scope.processedInspections = [];

                LanguageInspectionRes.inspections.query(function (inspections) {
                    $scope.ongoingInspections = inspections.filter(function (i) {
                        return !i.finishedAt;
                    });
                    $scope.processedInspections = inspections.filter(function (i) {
                        return i.finishedAt;
                    });
                });

                $scope.assignInspection = function(inspection) {
                    var dialog = dialogs.confirm($translate.instant('sitnet_confirm'),
                        $translate.instant('sitnet_confirm_assign_inspection'));
                    dialog.result.then(function () {
                        LanguageInspectionRes.assignment.update({id : inspection.id}, function() {
                            $location.path('exams/review/' + inspection.exam.id);
                        }, function(err) {
                            toastr.error(err);
                        })
                    });
                };

                $scope.showStatement = function (statement) {
                    var modalController = ["$scope", "$modalInstance", function ($scope, $modalInstance) {
                        $scope.statement = statement.comment;
                        $scope.ok = function () {
                            $modalInstance.close("Accepted");
                        };
                    }];

                    var modalInstance = $modal.open({
                        templateUrl: EXAM_CONF.TEMPLATES_PATH + 'maturity/show_inspection_statement.html',
                        backdrop: 'static',
                        keyboard: true,
                        controller: modalController,
                        resolve: {
                            statement: function () {
                                return statement.comment;
                            }
                        }
                    });

                    modalInstance.result.then(function () {
                        console.log("closed");
                    });
                };

                $scope.getOngoingInspectionsDetails = function () {
                    var amount = $scope.ongoingInspections.length.toString();
                    return $translate.instant('sitnet_ongoing_language_inspections_detail').replace('{0}', amount);
                };

                $scope.getProcessedInspectionsDetails = function () {
                    var amount = $scope.processedInspections.length.toString();
                    var year = moment().format('YYYY');
                    return $translate.instant('sitnet_processed_language_inspections_detail').replace('{0}', amount)
                        .replace('{1}', year);
                };



            }]);
}());
