/*
 * Copyright (c) 2017 The members of the EXAM Consortium (https://confluence.csc.fi/display/EXAM/Konsortio-organisaatio)
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl/licence-eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed
 * on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */

'use strict';

angular.module('app.review')
    .component('rInspectionComments', {
        templateUrl: '/assets/app/review/assessment/maturity/inspectionComments.template.html',
        bindings: {
            exam: '<',
            addingDisabled: '<',
            addingVisible: '<'
        },
        controller: ['$uibModal', 'ExamRes',
            function ($modal, ExamRes) {

                var vm = this;

                vm.addInspectionComment = function () {
                    $modal.open({
                        backdrop: 'static',
                        keyboard: true,
                        animation: true,
                        component: 'rInspectionComment'
                    }).result.then(function (params) {
                        ExamRes.inspectionComment.create({
                            id: vm.exam.id,
                            comment: params.comment
                        }, function (comment) {
                            vm.exam.inspectionComments.unshift(comment);
                        });
                    });
                };

            }

        ]
    });
