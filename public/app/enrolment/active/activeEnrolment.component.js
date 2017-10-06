'use strict';
angular.module('app.enrolment')
    .component('activeEnrolment', {
        templateUrl: '/assets/app/enrolment/active/activeEnrolment.template.html',
        bindings: {
            enrolment: '<',
            onRemoval: '&'
        },
        controller: ['$translate', 'dialogs', 'Enrolment', 'reservationService',
            function ($translate, dialogs, Enrolment, reservationService) {

                var vm = this;

                vm.removeReservation = function () {
                    reservationService.removeReservation(vm.enrolment);
                };

                vm.removeEnrolment = function () {
                    if (vm.enrolment.reservation) {
                        toastr.error($translate.instant('sitnet_cancel_reservation_first'));
                    } else {
                        dialogs.confirm($translate.instant('sitnet_confirm'),
                            $translate.instant('sitnet_are_you_sure')).result.then(
                            function () {
                                Enrolment.removeEnrolment(vm.enrolment).then(function () {
                                        vm.onRemoval({data: vm.enrolment});
                                    }
                                );
                            });
                    }

                };

                vm.addEnrolmentInformation = function () {
                    Enrolment.addEnrolmentInformation(vm.enrolment);
                };

                vm.showRoomGuide = function (hash) {
                    if (!vm.roomInstructions) {
                        Enrolment.getRoomInstructions(hash).success(function (room) {
                            var code = $translate.use();
                            vm.roomInstructions = room['roomInstruction' + code.toUpperCase()] || room.roomInstruction;
                        });
                    }
                };

                vm.showMaturityInstructions = function () {
                    Enrolment.showMaturityInstructions(vm.enrolment);
                };

            }
        ]
    });