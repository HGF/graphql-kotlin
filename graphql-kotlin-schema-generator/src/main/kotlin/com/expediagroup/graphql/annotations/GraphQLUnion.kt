package com.expediagroup.graphql.annotations

import kotlin.reflect.KClass

annotation class GraphQLUnion(val possibleClasses: Array<KClass<*>>, val name: String)
