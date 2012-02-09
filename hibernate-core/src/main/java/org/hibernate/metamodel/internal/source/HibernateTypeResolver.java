/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.metamodel.internal.source;

import java.util.Properties;

import org.hibernate.AssertionFailure;
import org.hibernate.metamodel.spi.binding.AbstractPluralAttributeElementBinding;
import org.hibernate.metamodel.spi.binding.AbstractPluralAttributeBinding;
import org.hibernate.metamodel.spi.binding.AttributeBinding;
import org.hibernate.metamodel.spi.binding.BasicPluralAttributeElementBinding;
import org.hibernate.metamodel.spi.binding.PluralAttributeElementNature;
import org.hibernate.metamodel.spi.binding.EntityBinding;
import org.hibernate.metamodel.spi.binding.EntityDiscriminator;
import org.hibernate.metamodel.spi.binding.HibernateTypeDescriptor;
import org.hibernate.metamodel.spi.binding.SingularAttributeBinding;
import org.hibernate.metamodel.spi.domain.SingularAttribute;
import org.hibernate.metamodel.spi.relational.Datatype;
import org.hibernate.metamodel.spi.relational.SimpleValue;
import org.hibernate.metamodel.spi.relational.Value;
import org.hibernate.metamodel.spi.source.MetadataImplementor;
import org.hibernate.type.Type;
import org.hibernate.type.TypeFactory;

/**
 * This is a TEMPORARY way to initialize Hibernate types.
 * This class will be removed when types are resolved properly.
 *
 * @author Gail Badner
 */
public class HibernateTypeResolver {

	private final MetadataImplementor metadata;

	public HibernateTypeResolver(MetadataImplementor metadata) {
		this.metadata = metadata;
	}

	public void resolve() {
		for ( EntityBinding entityBinding : metadata.getEntityBindings() ) {
			if ( entityBinding.getHierarchyDetails().getEntityDiscriminator() != null ) {
				resolveDiscriminatorTypeInformation( entityBinding.getHierarchyDetails().getEntityDiscriminator() );
			}
			for ( AttributeBinding attributeBinding : entityBinding.attributeBindings() ) {
				if ( SingularAttributeBinding.class.isInstance( attributeBinding ) ) {
	                System.out.println("singlular binding: " + attributeBinding.getAttribute().getName());
					resolveSingularAttributeTypeInformation(
							SingularAttributeBinding.class.cast( attributeBinding  )
					);
				}
				else if ( AbstractPluralAttributeBinding.class.isInstance( attributeBinding ) ) {
	                System.out.println("plural binding: " + attributeBinding.getAttribute().getName());
					resolvePluralAttributeTypeInformation(
							AbstractPluralAttributeBinding.class.cast( attributeBinding )
					);
				}
				else {
					throw new AssertionFailure( "Unknown type of AttributeBinding: " + attributeBinding.getClass().getName() );
				}
			}
		}
	}

	// perform any needed type resolutions for discriminator
	private void resolveDiscriminatorTypeInformation(EntityDiscriminator discriminator) {
		// perform any needed type resolutions for discriminator
		Type resolvedHibernateType = determineSingularTypeFromDescriptor( discriminator.getExplicitHibernateTypeDescriptor() );
		if ( resolvedHibernateType != null ) {
			pushHibernateTypeInformationDownIfNeeded(
					discriminator.getExplicitHibernateTypeDescriptor(),
					discriminator.getBoundValue(),
					resolvedHibernateType
			);
		}
	}

	private Type determineSingularTypeFromDescriptor(HibernateTypeDescriptor hibernateTypeDescriptor) {
		if ( hibernateTypeDescriptor.getResolvedTypeMapping() != null ) {
			return hibernateTypeDescriptor.getResolvedTypeMapping();
		}
		String typeName = determineTypeName( hibernateTypeDescriptor );
		Properties typeParameters = getTypeParameters( hibernateTypeDescriptor );
		return getHeuristicType( typeName, typeParameters );
	}

	private static String determineTypeName(HibernateTypeDescriptor hibernateTypeDescriptor) {
		return hibernateTypeDescriptor.getExplicitTypeName() != null
				? hibernateTypeDescriptor.getExplicitTypeName()
				: hibernateTypeDescriptor.getJavaTypeName();
	}

	private static Properties getTypeParameters(HibernateTypeDescriptor hibernateTypeDescriptor) {
		Properties typeParameters = new Properties( );
		if ( hibernateTypeDescriptor.getTypeParameters() != null ) {
			typeParameters.putAll( hibernateTypeDescriptor.getTypeParameters() );
		}
		return typeParameters;
	}

	// perform any needed type resolutions for SingularAttributeBinding
	private void resolveSingularAttributeTypeInformation(SingularAttributeBinding attributeBinding) {
		if ( attributeBinding.getHibernateTypeDescriptor().getResolvedTypeMapping() != null ) {
			return;
		}
		// we can determine the Hibernate Type if either:
		// 		1) the user explicitly named a Type in a HibernateTypeDescriptor
		// 		2) we know the java type of the attribute
		Type resolvedType;
		resolvedType = determineSingularTypeFromDescriptor( attributeBinding.getHibernateTypeDescriptor() );
		System.out.println("resolvedType: " + resolvedType);
		if ( resolvedType == null ) {
			if ( ! attributeBinding.getAttribute().isSingular() ) {
				throw new AssertionFailure( "SingularAttributeBinding object has a plural attribute: " + attributeBinding.getAttribute().getName() );
			}
			final SingularAttribute singularAttribute = ( SingularAttribute ) attributeBinding.getAttribute();
			if ( singularAttribute.getSingularAttributeType() != null ) {
				resolvedType = getHeuristicType(
						singularAttribute.getSingularAttributeType().getClassName(), new Properties()
				);
			}
		}
		if ( resolvedType != null ) {
			pushHibernateTypeInformationDownIfNeeded( attributeBinding, resolvedType );
		}
	}

	// perform any needed type resolutions for PluralAttributeBinding
	private void resolvePluralAttributeTypeInformation(AbstractPluralAttributeBinding attributeBinding) {
		if ( attributeBinding.getHibernateTypeDescriptor().getResolvedTypeMapping() != null ) {
			return;
		}
		Type resolvedType;
		// do NOT look at java type...
		//String typeName = determineTypeName( attributeBinding.getHibernateTypeDescriptor() );
		String typeName = attributeBinding.getHibernateTypeDescriptor().getExplicitTypeName();
		if ( typeName != null ) {
			resolvedType =
					metadata.getTypeResolver()
							.getTypeFactory()
							.customCollection(
									typeName,
									getTypeParameters( attributeBinding.getHibernateTypeDescriptor() ),
									attributeBinding.getAttribute().getName(),
									attributeBinding.getReferencedPropertyName(),
									attributeBinding.getPluralAttributeElementBinding().getPluralAttributeElementNature() ==
											PluralAttributeElementNature.COMPOSITE
							);
		}
		else {
			resolvedType = determineDefaultCollectionInformation( attributeBinding );
		}
		if ( resolvedType != null ) {
			pushHibernateTypeInformationDownIfNeeded(
					attributeBinding.getHibernateTypeDescriptor(),
					null,
					resolvedType );
		}
		resolveCollectionElementTypeInformation( attributeBinding.getPluralAttributeElementBinding() );
	}

	private Type determineDefaultCollectionInformation(AbstractPluralAttributeBinding attributeBinding) {
		final TypeFactory typeFactory = metadata.getTypeResolver().getTypeFactory();
		switch ( attributeBinding.getAttribute().getNature() ) {
			case SET: {
				return typeFactory.set(
						attributeBinding.getAttribute().getName(),
						attributeBinding.getReferencedPropertyName(),
						attributeBinding.getPluralAttributeElementBinding().getPluralAttributeElementNature() == PluralAttributeElementNature.COMPOSITE
				);
			}
			case BAG: {
				return typeFactory.bag(
						attributeBinding.getAttribute().getName(),
						attributeBinding.getReferencedPropertyName(),
						attributeBinding.getPluralAttributeElementBinding()
								.getPluralAttributeElementNature() == PluralAttributeElementNature.COMPOSITE
				);
			}
			default: {
				throw new UnsupportedOperationException(
						"Collection type not supported yet:" + attributeBinding.getAttribute().getNature()
				);
			}
		}
	}

	private void resolveCollectionElementTypeInformation(AbstractPluralAttributeElementBinding pluralAttributeElementBinding) {
		switch ( pluralAttributeElementBinding.getPluralAttributeElementNature() ) {
			case BASIC: {
				resolveBasicCollectionElement( BasicPluralAttributeElementBinding.class.cast(
						pluralAttributeElementBinding
				) );
				break;
			}
			case COMPOSITE:
			case ONE_TO_MANY:
			case MANY_TO_MANY:
			case MANY_TO_ANY: {
				throw new UnsupportedOperationException( "Collection element nature not supported yet: " + pluralAttributeElementBinding
						.getPluralAttributeElementNature() );
			}
			default: {
				throw new AssertionFailure( "Unknown collection element nature : " + pluralAttributeElementBinding.getPluralAttributeElementNature() );
			}
		}
	}

	private void resolveBasicCollectionElement(BasicPluralAttributeElementBinding basicCollectionElement) {
		Type resolvedHibernateType = determineSingularTypeFromDescriptor( basicCollectionElement.getHibernateTypeDescriptor() );
		if ( resolvedHibernateType != null ) {
			pushHibernateTypeInformationDownIfNeeded(
					basicCollectionElement.getHibernateTypeDescriptor(),
					basicCollectionElement.getRelationalValue(),
					resolvedHibernateType
			);
		}
	}

	private Type getHeuristicType(String typeName, Properties typeParameters) {
		if ( typeName != null ) {
			try {
				return metadata.getTypeResolver().heuristicType( typeName, typeParameters );
			}
			catch (Exception ignore) {
			}
		}

		return null;
	}

	private void pushHibernateTypeInformationDownIfNeeded(SingularAttributeBinding attributeBinding, Type resolvedHibernateType) {

		final HibernateTypeDescriptor hibernateTypeDescriptor = attributeBinding.getHibernateTypeDescriptor();
		final SingularAttribute singularAttribute = SingularAttribute.class.cast( attributeBinding.getAttribute() );
		final Value value = attributeBinding.getValue();
		if ( ! singularAttribute.isTypeResolved() && hibernateTypeDescriptor.getJavaTypeName() != null ) {
			singularAttribute.resolveType( metadata.makeJavaType( hibernateTypeDescriptor.getJavaTypeName() ) );
		}

		// sql type information ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		this.pushHibernateTypeInformationDownIfNeeded(
				hibernateTypeDescriptor, value, resolvedHibernateType
		);
	}

	private void pushHibernateTypeInformationDownIfNeeded(
			HibernateTypeDescriptor hibernateTypeDescriptor,
			Value value,
			Type resolvedHibernateType) {
		if ( resolvedHibernateType == null ) {
			return;
		}
		if ( hibernateTypeDescriptor.getResolvedTypeMapping() == null ) {
			hibernateTypeDescriptor.setResolvedTypeMapping( resolvedHibernateType );
		}

		// java type information ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

		if ( hibernateTypeDescriptor.getJavaTypeName() == null ) {
			hibernateTypeDescriptor.setJavaTypeName( resolvedHibernateType.getReturnedClass().getName() );
		}

	   // todo : this can be made a lot smarter, but for now this will suffice.  currently we only handle single value bindings

	   if ( SimpleValue.class.isInstance( value ) ) {
		   SimpleValue simpleValue = (SimpleValue) value;
		   if ( simpleValue.getDatatype() == null ) {
			   simpleValue.setDatatype(
					   new Datatype(
							   resolvedHibernateType.sqlTypes( metadata )[0],
							   resolvedHibernateType.getName(),
							   resolvedHibernateType.getReturnedClass()
					   )
			   );
		   }
	   }
	}
}
