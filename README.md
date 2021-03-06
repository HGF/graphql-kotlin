# GraphQL Kotlin

[![Build Status](https://travis-ci.org/ExpediaDotCom/graphql-kotlin.svg?branch=master)](https://travis-ci.org/ExpediaDotCom/graphql-kotlin)

Most GraphQL libraries for the JVM require developers to maintain two sources of truth for their GraphQL API, the schema and the corresponding code (data fetchers and types). Given the similarities between Kotlin and GraphQL, such as the ability to define nullable/non-nullable types, a schema should be able to be generated from Kotlin code without any separate schema specification. `bex-api-graphql` builds upon `graphql-java` to allow code-only GraphQL services to be built.

For information on GraphQL, please visit [the GraphQL website](https://graphql.org/).

For information on `graphql-java`, please visit [GraphQL Java](https://graphql-java.readthedocs.io/en/latest/).

# Getting started

## Installation

Using a JVM dependency manager, simply link `bex-api-graphql` to your project.

With Maven:

```xml
<dependency>
  <groupId>com.expedia.www</groupId>
  <artifactId>graphql-kotlin</artifactId>
  <version>0.0.1</version>
</dependency>
```

With Gradle:

```groovy
compile(group: 'com.expedia.www', artifact: 'graphql-kotlin', version: '0.0.1')
```

## Generating a schema

`bex-api-sdk` provides a single function, `toSchema`, to generate a schema from Kotlin objects.

```kotlin
import graphql.schema.GraphQLSchema
import com.expedia.api.graphql.toSchema

class Query {
  fun getNumber() = 1
}

val schema: GraphQLSchema = toSchema(listOf(TopLevelObjectDef(Query())))
```

generates a `GraphQLSchema` with IDL that looks like this:

```graphql
type TopLevelQuery {
  getNumber: Int!
}
```

The `GraphQLSchema` generated can be used to expose a GraphQL API endpoint.

## Class `TopLevelObjectDef`

`toSchema` uses Kotlin reflection to build a GraphQL schema from given classes using `graphql-java`'s schema builder. We don't just pass a `KClass` though, we have to actually pass an object, because the functions on the object are transformed into the query or mutation's data fetchers. In most cases, a `TopLevelObjectDef` can be constructed with just an object:

```kotlin
class Query {
  fun getNumber() = 1
}

val def = TopLevelObjectDef(query)

toSchema(listOf(def))
```

In the above case, `toSchema` will use `query::class` as the reflection target, and `query` as the data fetcher target.

In a lot of cases, such as with Spring AOP, the object (or bean) being used to generate a schema is a dynamic proxy. In this case, `query::class` is not `Query`, but rather a generated class that will confuse the schema generator. To specify the `KClass` to use for reflection on a proxy, pass the class to `TopLevelObjectDef`:

```kotlin
@Component
class Query {
  @Timed
  fun getNumber() = 1
}

val def = TopLevelObjectDef(query, Query::class)

toSchema(listOf(def))
``` 

More about writing schemas with Kotlin below.

# Writing schemas with Kotlin

## Basics

`toSchema` requires a list of `TopLevelObjectDef` objects for both queries and mutations to be included in the GraphQL schema.

A query type is simply a Kotlin class that specifies *fields*, which can be functions or properties:

```kotlin
class WidgetQuery {
  val widgetCount: Int
  fun widgetById(id: Int): Widget? {
    // grabs widget from a data source
  } 
}
```

will generate:

```graphql
type TopLevelQuery {
  widgetCount: Int!
  widgetById(id: Int!): Widget
}
```

Any `public` properties and functions defined on a query or mutation Kotlin class will be translated into GraphQL fields on the object type. `toSchema` will recursively use Kotlin reflection to generate all object types, fields, arguments and enums.

### Types

For the most part, `bex-api-graphql` can directly map most Kotlin "primitive" types to standard GraphQL scalar types:

#### Scalars

| Kotlin Type         | GraphQL Type |
|---------------------|--------------|
| `kotlin.Int`        | `Int`        |
| `kotlin.Long`       | `Long`       |
| `kotlin.Short`      | `Short`      |
| `kotlin.Float`      | `Float`      |
| `kotlin.Double`     | `Float`      |
| `kotlin.BigInteger` | `BigInteger` |
| `kotlin.BigDecimal` | `BigDecimal` |
| `kotlin.Char`       | `Char`       |
| `kotlin.String`     | `String`     |
| `kotlin.Boolean`    | `Boolean`    |

`bex-api-graphql` also ships with a few extension scalar types:

#### Extension Scalars

- `UUID` is a custom scalar type (string) that provides runtime validation to ensure the string is a valid v4 UUID. Fields and arguments of type `java.util.UUID` are mapped to this custom scalar.
- `URL` is a custom scalar type (string) that provides runtime validation to ensure the string is a valid URL (protocol, host, port, file, etc). Fields and arguments of type `java.util.URL` are mapped to this custom scalar.

#### List Types

Both `kotlin.Array` and `kotlin.collections.List` are automatically mapped to the GraphQL `List` type. Type arguments provided to Kotlin collections are used as the type arguments in the GraphQL `List` type. 

```kotlin
class SimpleQuery {
  val numbers = listOf(1, 2, 3)
}
```

The above Kotlin class would produce the following GraphQL schema:

```graphql
schema {
  query: TopLevelQuery
}

type TopLevelQuery {
  numbers: List[Int!]
}
```

#### Nullability

### Fields

### Arguments

### Enums

### Interfaces

TBD

## Subscriptions

TBD

## Annotations

`bex-api-graphql` ships with a number of annotation classes to allow you to enhance your GraphQL schema for things that can't be directly derived from Kotlin reflection.

### `@Context`

All GraphQL servers have a concept of a "context". A GraphQL context contains metadata that is useful to the GraphQL server, but shouldn't necessarily be part of the GraphQL query's API. A prime example of something that is appropriate for the GraphQL context would be trace headers for an OpenTracing system such as Zipkin or Haystack. The GraphQL query itself does not need the information to perform its function, but the server itself needs the information to ensure observability.

The contents of the GraphQL context vary across GraphQL applications. For JVM based applications, `graphql-java` provides a `GraphQLContext` interface that can be extended.

Simply add `@Context` to any argument to a field, and the GraphQL context for the environment will be injected. These arguments will be omitted by the schema generator.

```kotlin
class Query {
  fun doSomething(
    @Description("A value") value: Int,
    @Context context: MyGraphQLContextImpl
  ): Boolean! {
    doSomething(context.getResult());
    return true
  }
}
```

The above query would produce the following GraphQL schema:

```graphql
schema {
  query: TopLevelQuery
}

type TopLevelQuery {
  doSomething(value: Int!): Boolean!
}
```

Note that the `@Context` annotated argument is not reflected in the GraphQL schema.

### @Ignore

There are two ways to ensure the GraphQL schema generation omits fields when using Kotlin reflection:

The first is by marking the field as `private` scope. The second method is by annotating the field with `@Ignore`:

```kotlin
class Query {
  @Ignore
  val notPartOfSchema = "ignore me!"

  fun doSomething(
    value: Int
  ): Boolean! {
    return true
  }
}
```

The above query would produce the following GraphQL schema:

```graphql
schema {
  query: TopLevelQuery
}

type TopLevelQuery {
  doSomething(value: Int!): Boolean!
}
```

Note that the public property `notPartOfSchema` is not included in the schema.

### `@Description`

Since Javadocs are not available at runtime for introspection, `bex-api-graphql` includes an annotation class `@Description` that can be used to add schema descriptions to *any* GraphQL schema element:

```kotlin
@Description("A useful widget")
data class Widget(
  @Description("The widget's value")
  val value: Boolean?
)

class Query {
  @Description("Does something very special")
  fun doSomething(
    @Description("The special ingredient") value: Int
  ): Widget! {
    return Widget(value !== 1)
  }
}
```

The above query would produce the following GraphQL schema:

```graphql
schema {
  query: TopLevelQuery
}

# A useful widget
type Widget {
  # The widget's value
  value: Boolean
}

type TopLevelQuery {
  # Does something very useful
  doSomething(
    # The special ingredient
    value: Int!
  ): Boolean!
}
```

## Configuration

### Documentation Enforcement
