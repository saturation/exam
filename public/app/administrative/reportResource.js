(function () {
    'use strict';
    angular.module("exam.resources")
        .factory("ReportResource", ['$resource', function ($resource) {
            return {
                resbydate: $resource(
                    "/statistics/resbydate/:roomId/:from/:to",
                    {
                        id: "@roomId",
                        from: "@from",
                        to: "@to"
                    },
                    {
                        "get": {method: "GET", params: {id: "@roomId", from: "@from", to: "@to"}}
                    }),
                examnames: $resource(
                    "/statistics/examnames", null,
                    {

                    }),
                reviewsByDate: $resource(
                    "/statistics/reviewsbydate/:from/:to",
                    {
                        from: "@from",
                        to: "@to"
                    },
                    {
                        "get": {method: "GET", params: {from: "@from", to: "@to"}}
                    }),
                teacherExamsByDate: $resource(
                    "/statistics/teacherexamsbydate/:uid/:from/:to",
                    {
                        uid: "@uid",
                        from: "@from",
                        to: "@to"
                    },
                    {
                        "get": {method: "GET", params: {uid: "@uid", from: "@from", to: "@to"}}
                    })
            };
        }]);
}());